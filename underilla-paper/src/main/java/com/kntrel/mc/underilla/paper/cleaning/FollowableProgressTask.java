package com.kntrel.mc.underilla.paper.cleaning;

import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.IntegerKeys;
import com.kntrel.mc.underilla.paper.selector.Selector;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FollowableProgressTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(FollowableProgressTask.class);
    protected final Selector selector;
    protected final int taskID;
    protected final int tasksCount;
    protected long printTime;
    protected long printTimeEachXMs;
    protected boolean stop;

    public FollowableProgressTask(int taskID, int tasksCount, Selector selector) {
        this.taskID = taskID;
        this.tasksCount = tasksCount;
        this.selector = selector;
        printTime = 0;
        printTimeEachXMs = Underilla.MS_PER_SECOND * Underilla.getUnderillaConfig().getInt(IntegerKeys.PRINT_PROGRESS_EVERY_X_SECONDS);
        stop = false;
    }
    public FollowableProgressTask(int taskID, int tasksCount) { this(taskID, tasksCount, Underilla.getUnderillaConfig().getSelector()); }

    abstract public void run();

    public Selector stop() {
        stop = true;
        return selector;
    }

    protected void printProgress(long processed, long startTime) {
        printProgress(processed, startTime, selector.progress(), taskID, tasksCount, null);
    }
    protected void printProgressIfNeeded(long processed, long startTime) {
        if (printTime + printTimeEachXMs < System.currentTimeMillis()) {
            printTime = System.currentTimeMillis();
            printProgress(processed, startTime);
        }
    }

    public static void printProgress(long processed, long startTime, double progress, int taskID, int tasksCount, String extraString) {
        long timeForFullProgress = (long) ((System.currentTimeMillis() - startTime) / progress);
        long timeForFullProgressLeft = timeForFullProgress - (System.currentTimeMillis() - startTime);
        extraString = extraString == null ? "" : " " + extraString;
        String taskIDCount = tasksCount > 1 ? " (" + taskID + "/" + tasksCount + ")" : "";
        LOGGER.info("Task{} Progress: {}   {} ETA: {}{}", taskIDCount, processed, doubleToPercent(progress),
                Duration.ofMillis(timeForFullProgressLeft), extraString);
    }
    private static String doubleToPercent(double d) { return String.format("%.4f", d * 100) + "%"; }
}
