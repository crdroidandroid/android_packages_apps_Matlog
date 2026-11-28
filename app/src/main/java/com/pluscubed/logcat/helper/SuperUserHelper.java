package com.pluscubed.logcat.helper;

import com.pluscubed.logcat.util.UtilLogger;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Starting in JellyBean, the READ_LOGS permission must be requested as super user
 * or else you can only read your own app's logs.
 * 
 * This class contains helper methods to correct the problem.
 */
public class SuperUserHelper {

    private static final Pattern PID_PATTERN = Pattern.compile("\\d+");
    private static final Pattern SPACES_PATTERN = Pattern.compile("\\s+");
    private static UtilLogger log = new UtilLogger(SuperUserHelper.class);
    private static boolean failedToObtainRoot = true;

    private static List<Integer> getAllRelatedPids(final int pid) {
        List<Integer> result = new ArrayList<>();
        result.add(pid);
        // use 'ps' to get this pid and all pids that are related to it (e.g. spawned by it)
        try {

            final Process suProcess = Runtime.getRuntime().exec("su");

            new Thread(new Runnable() {

                @Override
                public void run() {
                    PrintStream outputStream = null;
                    try {
                        outputStream = new PrintStream(new BufferedOutputStream(suProcess.getOutputStream(), 8192));
                        outputStream.println("ps");
                        outputStream.println("exit");
                        outputStream.flush();
                    } finally {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    }

                }
            }).run();

            if (suProcess != null) {
                try {
                    suProcess.waitFor();
                } catch (InterruptedException e) {
                    log.e(e, "cannot get pids");
                }
            }


            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()), 8192);
                while (bufferedReader.ready()) {
                    String[] line = SPACES_PATTERN.split(bufferedReader.readLine());
                    if (line.length >= 3) {
                        try {
                            if (pid == Integer.parseInt(line[2])) {
                                result.add(Integer.parseInt(line[1]));
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
        } catch (IOException e1) {
            log.e(e1, "cannot get process ids");
        }

        return result;
    }

    public static void destroy(Process process) {
        // stupid method for getting the pid, but it actually works
        Matcher matcher = PID_PATTERN.matcher(process.toString());
        matcher.find();
        int pid = Integer.parseInt(matcher.group());
        List<Integer> allRelatedPids = getAllRelatedPids(pid);
        log.d("Killing %s", allRelatedPids);
        for (Integer relatedPid : allRelatedPids) {
            destroyPid(relatedPid);
        }

    }

    private static void destroyPid(int pid) {

        Process suProcess = null;
        PrintStream outputStream = null;
        try {
            suProcess = Runtime.getRuntime().exec("su");
            outputStream = new PrintStream(new BufferedOutputStream(suProcess.getOutputStream(), 8192));
            outputStream.println("kill " + pid);
            outputStream.println("exit");
            outputStream.flush();
        } catch (IOException e) {
            log.e(e, "cannot kill process " + pid);
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            if (suProcess != null) {
                try {
                    suProcess.waitFor();
                } catch (InterruptedException e) {
                    log.e(e, "cannot kill process " + pid);
                }
            }
        }
    }

    public static boolean isFailedToObtainRoot() {
        return failedToObtainRoot;
    }
}
