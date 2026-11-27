/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

// Serves the kernel log and pstore files to clients of the matlog_dmesg socket.
// Commands: 'd' dumps the kernel log, 'f' also follows it, 'l' lists pstore files
// as "name\tsize\tmtime" lines and 'p' followed by "name\n" sends one pstore file.

#define LOG_TAG "matlog_dmesg"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <unistd.h>

#include <memory>
#include <string>
#include <thread>

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <cutils/sockets.h>

using android::base::StringPrintf;
using android::base::unique_fd;

namespace {

constexpr char kSocketName[] = "matlog_dmesg";
constexpr char kCommandDump = 'd';
constexpr char kCommandFollow = 'f';
constexpr char kCommandPstoreList = 'l';
constexpr char kCommandPstoreFile = 'p';
constexpr char kPstoreDir[] = "/sys/fs/pstore";
constexpr size_t kMaxNameLength = 255;
constexpr int kCommandTimeoutMs = 5000;
constexpr size_t kRecordSize = 8192;

bool WriteAll(int fd, const char* data, size_t length) {
    while (length > 0) {
        ssize_t written = TEMP_FAILURE_RETRY(write(fd, data, length));
        if (written <= 0) return false;
        data += written;
        length -= written;
    }
    return true;
}

// "level,seq,usec,flags;message\n" becomes "[  seconds.micros] <level> message\n"
size_t FormatRecord(char* record, char* out, size_t size) {
    char* message = strchr(record, ';');
    if (message == nullptr) return 0;
    *message++ = '\0';
    char* end = strchr(message, '\n');
    if (end != nullptr) *end = '\0';

    unsigned int priority;
    unsigned long long seq, usec;
    if (sscanf(record, "%u,%llu,%llu", &priority, &seq, &usec) != 3) return 0;

    int length = snprintf(out, size, "[%5llu.%06llu] <%u> %s\n", usec / 1000000, usec % 1000000,
                          priority & 7, message);
    if (length <= 0) return 0;
    if (static_cast<size_t>(length) >= size) {
        out[size - 2] = '\n';
        return size - 1;
    }
    return length;
}

bool ReadCommand(int client, char* command) {
    pollfd pfd = {client, POLLIN, 0};
    if (TEMP_FAILURE_RETRY(poll(&pfd, 1, kCommandTimeoutMs)) <= 0) return false;
    return TEMP_FAILURE_RETRY(read(client, command, 1)) == 1;
}

bool ReadName(int client, std::string* name) {
    char c;
    while (ReadCommand(client, &c)) {
        if (c == '\n') return true;
        if (name->size() >= kMaxNameLength) return false;
        name->push_back(c);
    }
    return false;
}

void ListPstore(int client) {
    std::unique_ptr<DIR, decltype(&closedir)> dir(opendir(kPstoreDir), closedir);
    if (!dir) return;
    while (dirent* entry = readdir(dir.get())) {
        if (entry->d_name[0] == '.') continue;
        std::string path = StringPrintf("%s/%s", kPstoreDir, entry->d_name);
        struct stat st;
        if (stat(path.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) continue;
        std::string line = StringPrintf("%s\t%lld\t%lld\n", entry->d_name,
                                        static_cast<long long>(st.st_size),
                                        static_cast<long long>(st.st_mtime));
        if (!WriteAll(client, line.data(), line.size())) return;
    }
}

void SendPstoreFile(int client, const std::string& name) {
    if (name.empty() || name[0] == '.' || name.find('/') != std::string::npos) return;
    std::string path = StringPrintf("%s/%s", kPstoreDir, name.c_str());
    unique_fd fd(TEMP_FAILURE_RETRY(open(path.c_str(), O_RDONLY | O_NOFOLLOW | O_CLOEXEC)));
    if (fd < 0) {
        PLOG(ERROR) << "Unable to open " << path;
        return;
    }
    char buffer[kRecordSize];
    ssize_t length;
    while ((length = TEMP_FAILURE_RETRY(read(fd.get(), buffer, sizeof(buffer)))) > 0) {
        if (!WriteAll(client, buffer, length)) return;
    }
}

void Serve(unique_fd client) {
    char command;
    if (!ReadCommand(client.get(), &command)) return;
    if (command == kCommandPstoreList) {
        ListPstore(client.get());
        return;
    }
    if (command == kCommandPstoreFile) {
        std::string name;
        if (ReadName(client.get(), &name)) SendPstoreFile(client.get(), name);
        return;
    }
    bool follow = command == kCommandFollow;
    if (!follow && command != kCommandDump) return;

    unique_fd kmsg(TEMP_FAILURE_RETRY(open("/dev/kmsg", O_RDONLY | O_NONBLOCK | O_CLOEXEC)));
    if (kmsg < 0) {
        PLOG(ERROR) << "Unable to open /dev/kmsg";
        return;
    }

    char record[kRecordSize];
    char out[kRecordSize + 64];
    pollfd fds[2] = {{kmsg.get(), POLLIN, 0}, {client.get(), POLLIN, 0}};
    while (true) {
        ssize_t length = read(kmsg.get(), record, sizeof(record) - 1);
        if (length < 0) {
            // older records were overwritten while reading
            if (errno == EPIPE || errno == EINTR) continue;
            if (errno != EAGAIN) {
                PLOG(ERROR) << "Unable to read /dev/kmsg";
                return;
            }
            if (!follow) return;
            if (TEMP_FAILURE_RETRY(poll(fds, 2, -1)) < 0) return;
            // the client never writes again, so any event on it means it went away
            if (fds[1].revents != 0) return;
            continue;
        }
        record[length] = '\0';
        size_t formatted = FormatRecord(record, out, sizeof(out));
        if (formatted > 0 && !WriteAll(client.get(), out, formatted)) return;
    }
}

}  // namespace

int main() {
    signal(SIGPIPE, SIG_IGN);

    int server = android_get_control_socket(kSocketName);
    if (server < 0) {
        LOG(ERROR) << "Unable to get the " << kSocketName << " socket";
        return 1;
    }
    if (listen(server, 4) < 0) {
        PLOG(ERROR) << "Unable to listen";
        return 1;
    }

    while (true) {
        int client = TEMP_FAILURE_RETRY(accept4(server, nullptr, nullptr, SOCK_CLOEXEC));
        if (client < 0) {
            PLOG(ERROR) << "Unable to accept";
            sleep(1);
            continue;
        }
        std::thread(Serve, unique_fd(client)).detach();
    }
}
