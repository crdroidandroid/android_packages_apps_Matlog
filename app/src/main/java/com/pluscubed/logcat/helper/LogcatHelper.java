package com.pluscubed.logcat.helper;

import com.pluscubed.logcat.util.UtilLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogcatHelper {

    public static final String BUFFER_MAIN = "main";
    public static final String BUFFER_EVENTS = "events";
    public static final String BUFFER_RADIO = "radio";

    private static UtilLogger log = new UtilLogger(LogcatHelper.class);

    public static Process getLogcatProcess(String buffer, String lastLine) throws IOException {

        List<String> args = getLogcatArgs(buffer);
        // Android 16 can start a non-root reader at the live tail. Start at the
        // recording boundary instead, so SingleLogcatReader can flush the
        // initial batch before switching to live updates.
        args.add("-T");
        args.add(getLogcatStart(lastLine));
        Process process = RuntimeHelper.exec(args);

        return process;
    }

    private static List<String> getLogcatArgs(String buffer) {
        List<String> args = new ArrayList<String>(Arrays.asList("logcat", "-v", "time"));

        // for some reason, adding -b main excludes log output from AndroidRuntime runtime exceptions,
        // whereas just leaving it blank keeps them in.  So do not specify the buffer if it is "main"
        if (!buffer.equals(BUFFER_MAIN)) {
            args.add("-b");
            args.add(buffer);
        }

        return args;
    }

    private static String getLogcatStart(String lastLine) {
        // The "time" format starts with "MM-DD HH:MM:SS.mmm" (18 chars),
        // which logcat accepts as a -T start time. Fall back to one line when
        // there is no valid recording boundary.
        if (lastLine != null && lastLine.length() >= 18
                && Character.isDigit(lastLine.charAt(0))) {
            return lastLine.substring(0, 18);
        }
        return "1";
    }

    public static String getLastLogLine(String buffer) {
        Process dumpLogcatProcess = null;
        BufferedReader reader = null;
        String result = null;
        try {

            List<String> args = getLogcatArgs(buffer);
            args.add("-d"); // -d just dumps the whole thing

            dumpLogcatProcess = RuntimeHelper.exec(args);
            reader = new BufferedReader(new InputStreamReader(dumpLogcatProcess
                    .getInputStream()), 8192);

            String line;
            while ((line = reader.readLine()) != null) {
                result = line;
            }
        } catch (IOException e) {
            log.e(e, "unexpected exception");
        } finally {
            if (dumpLogcatProcess != null) {
                RuntimeHelper.destroy(dumpLogcatProcess);
                log.d("destroyed 1 dump logcat process");
            }
            // post-jellybean, we just kill the process, so there's no need
            // to close the bufferedReader.  Anyway, it just hangs.
            if (VersionHelper.getVersionSdkIntCompat() < VersionHelper.VERSION_JELLYBEAN
                    && reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.e(e, "unexpected exception");
                }
            }
        }

        return result;
    }
}
