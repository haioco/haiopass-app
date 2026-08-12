package com.haio.bypass.proxy;

import java.io.FileDescriptor;
import java.lang.reflect.Field;

public final class ProcessUtils {

    private ProcessUtils() {}

    public static int getPid(Process process) {
        try {
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            return pidField.getInt(process);
        } catch (NoSuchFieldException e) {
            // Fallback: try the public Java 9+ method
            try {
                return (int) Process.class.getMethod("pid").invoke(process);
            } catch (Exception ex) {
                throw new RuntimeException("Cannot get PID", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot get PID", e);
        }
    }

    public static int getFdInt(FileDescriptor fd) {
        try {
            Field fdField = FileDescriptor.class.getDeclaredField("fd");
            fdField.setAccessible(true);
            return fdField.getInt(fd);
        } catch (Exception e) {
            throw new RuntimeException("Cannot get fd int from FileDescriptor", e);
        }
    }
}
