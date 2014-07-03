/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.service;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

/**
 * Result of the command execution
 * See also {@link android.content.SyncStats}
 * @author yvolk@yurivolkov.com
 */
public final class CommandResult implements Parcelable {
    static final int INITIAL_NUMBER_OF_RETRIES = 10;
    
    private long createdDate = System.currentTimeMillis();
    private long lastExecutedDate = 0;
    private int executionCount = 0;
    private int retriesLeft = 0;
    
    private boolean executed = false;
    private long numAuthExceptions = 0;
    private long numIoExceptions = 0;
    private long numParseExceptions = 0;

    private long itemId = 0;
    
    // 0 means these values were not set
    private int hourlyLimit = 0;
    private int remainingHits = 0;
    
    // Counters to use for user notifications
    private int messagesAdded = 0;
    private int mentionsAdded = 0;
    private int directedAdded = 0;
    private int downloadedCount = 0;

    public CommandResult() {
    }

    CommandResult forOneExecStep() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        CommandResult oneStepResult = new CommandResult(parcel);
        oneStepResult.prepareForLaunch();
        parcel.recycle();
        return oneStepResult;
    }
    
    void accumulateOneStep(CommandResult oneStepResult) {
        numAuthExceptions += oneStepResult.numAuthExceptions;
        numIoExceptions += oneStepResult.numIoExceptions;
        numParseExceptions += oneStepResult.numParseExceptions;
        if (itemId == 0) {
            itemId = oneStepResult.itemId;
        }
        hourlyLimit = oneStepResult.hourlyLimit;
        remainingHits = oneStepResult.remainingHits;
        messagesAdded += oneStepResult.messagesAdded;
        mentionsAdded += oneStepResult.mentionsAdded;
        directedAdded += oneStepResult.directedAdded;
        downloadedCount += oneStepResult.downloadedCount;
    }
    
    public static final Creator<CommandResult> CREATOR = new Creator<CommandResult>() {
        @Override
        public CommandResult createFromParcel(Parcel in) {
            return new CommandResult(in);
        }

        @Override
        public CommandResult[] newArray(int size) {
            return new CommandResult[size];
        }
    };
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(createdDate);
        dest.writeLong(lastExecutedDate);
        dest.writeInt(executionCount);
        dest.writeInt(retriesLeft);
        dest.writeLong(numAuthExceptions);
        dest.writeLong(numIoExceptions);
        dest.writeLong(numParseExceptions);
        dest.writeLong(itemId);
        dest.writeInt(hourlyLimit);
        dest.writeInt(remainingHits);
        dest.writeInt(downloadedCount);
    }
    
    public CommandResult(Parcel parcel) {
        createdDate = parcel.readLong();
        lastExecutedDate = parcel.readLong();
        executionCount = parcel.readInt();
        retriesLeft = parcel.readInt();
        numAuthExceptions = parcel.readLong();
        numIoExceptions = parcel.readLong();
        numParseExceptions = parcel.readLong();
        itemId = parcel.readLong();
        hourlyLimit = parcel.readInt();
        remainingHits = parcel.readInt();
        downloadedCount = parcel.readInt();
    }
    
    void saveToSharedPreferences(android.content.SharedPreferences.Editor ed, int index) {
        String si = Integer.toString(index);
        ed.putLong(IntentExtra.EXTRA_CREATED_DATE.key + si, createdDate);
        ed.putLong(IntentExtra.EXTRA_LAST_EXECUTED_DATE.key + si, lastExecutedDate);
        ed.putInt(IntentExtra.EXTRA_EXECUTION_COUNT.key + si, executionCount);
        ed.putInt(IntentExtra.EXTRA_RETRIES_LEFT.key + si, retriesLeft);
        ed.putLong(IntentExtra.EXTRA_NUM_AUTH_EXCEPTIONS.key + si, numAuthExceptions);
        ed.putLong(IntentExtra.EXTRA_NUM_IO_EXCEPTIONS.key + si, numIoExceptions);
        ed.putLong(IntentExtra.EXTRA_NUM_PARSE_EXCEPTIONS.key + si, numParseExceptions);
        ed.putInt(IntentExtra.EXTRA_DOWNLOADED_COUNT.key + si, downloadedCount);
    }

    void loadFromSharedPreferences(SharedPreferences sp, int index) {
        String si = Integer.toString(index);
        createdDate = sp.getLong(IntentExtra.EXTRA_CREATED_DATE.key + si, createdDate);
        lastExecutedDate = sp.getLong(IntentExtra.EXTRA_LAST_EXECUTED_DATE.key + si, lastExecutedDate);
        executionCount = sp.getInt(IntentExtra.EXTRA_EXECUTION_COUNT.key + si, executionCount);
        retriesLeft = sp.getInt(IntentExtra.EXTRA_RETRIES_LEFT.key + si, retriesLeft);
        numAuthExceptions = sp.getLong(IntentExtra.EXTRA_NUM_AUTH_EXCEPTIONS.key + si, numAuthExceptions);
        numIoExceptions = sp.getLong(IntentExtra.EXTRA_NUM_IO_EXCEPTIONS.key + si, numIoExceptions);
        numParseExceptions = sp.getLong(IntentExtra.EXTRA_NUM_PARSE_EXCEPTIONS.key + si, numParseExceptions);
        downloadedCount = sp.getInt(IntentExtra.EXTRA_DOWNLOADED_COUNT.key + si, downloadedCount);
    }

    public int getExecutionCount() {
        return executionCount;
    }

    public boolean hasError() {
        return hasSoftError() || hasHardError();
    }
    
    public boolean hasHardError() {
        return numAuthExceptions > 0 || numParseExceptions > 0;
    }

    public boolean hasSoftError() {
        return numIoExceptions > 0;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public void setSoftErrorIfNotOk(boolean ok) {
        if (!ok) {
            incrementNumIoExceptions();
        }
    }
    
    public static String toString(CommandResult commandResult) {
        return commandResult == null ? "(result is null)" : commandResult.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder message = new StringBuilder();
        message.append("created:" + RelativeTime.getDifference(MyContextHolder.get().context(), createdDate) + ",");
        if (executionCount > 0) {
            message.append("executed:" + executionCount + ",");
            message.append("last:" + RelativeTime.getDifference(MyContextHolder.get().context(), lastExecutedDate) + ",");
            if (retriesLeft > 0) {
                message.append("retriesLeft:" + retriesLeft + ",");
            }
            if (!hasError()) {
                message.append("error:None,");
            }
        }
        if (hasError()) {
            message.append("error:" + (hasHardError() ? "Hard" : "Soft") + ",");
        }
        if (downloadedCount > 0) {
            message.append("downloaded:" + downloadedCount + ",");
        }
        if (messagesAdded > 0) {
            message.append("messages:" + messagesAdded + ",");
        }
        if (mentionsAdded > 0) {
            message.append("mentions:" + mentionsAdded + ",");
        }
        if (directedAdded > 0) {
            message.append("directed:" + directedAdded + ",");
        }
        
        return MyLog.formatKeyValue("CommandResult", message);
    }
    
    public long getNumAuthExceptions() {
        return numAuthExceptions;
    }

    protected void incrementNumAuthExceptions() {
        numAuthExceptions++;
    }

    public long getNumIoExceptions() {
        return numIoExceptions;
    }

    public void incrementNumIoExceptions() {
        numIoExceptions++;
    }

    public long getNumParseExceptions() {
        return numParseExceptions;
    }

    void incrementParseExceptions() {
        numParseExceptions++;
    }

    public int getHourlyLimit() {
        return hourlyLimit;
    }

    protected void setHourlyLimit(int hourlyLimit) {
        this.hourlyLimit = hourlyLimit;
    }

    public int getRemainingHits() {
        return remainingHits;
    }

    protected void setRemainingHits(int remainingHits) {
        this.remainingHits = remainingHits;
    }

    public void incrementMessagesCount(TimelineTypeEnum timelineType) {
        switch (timelineType) {
            case HOME:
                messagesAdded++;
                break;
            case DIRECT:
                directedAdded++;
                break;
            default:
                break;
        }
    }

    public void incrementMentionsCount() {
        mentionsAdded++;
    }

    public void incrementDownloadedCount() {
        downloadedCount++;
    }

    public int getDownloadedCount() {
        return downloadedCount;
    }
    
    public int getMessagesAdded() {
        return messagesAdded;
    }

    public int getMentionsAdded() {
        return mentionsAdded;
    }

    public int getDirectedAdded() {
        return directedAdded;
    }
    
    protected int getRetriesLeft() {
        return retriesLeft;
    }
    
    void resetRetries(CommandEnum command) {
        retriesLeft = INITIAL_NUMBER_OF_RETRIES;
        switch (command) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
            case RATE_LIMIT_STATUS:
            case SEARCH_MESSAGE:
                retriesLeft = 0;
                break;
            default:
                break;
        }
    }

    void prepareForLaunch() {
        executed = false;
        
        numAuthExceptions = 0;
        numIoExceptions = 0;
        numParseExceptions = 0;
        
        itemId = 0;
        
        hourlyLimit = 0;
        remainingHits = 0;

        messagesAdded = 0;
        mentionsAdded = 0;
        directedAdded = 0;
        downloadedCount = 0;
    }
    
    void afterExecutionEnded() {
        executed = true;
        executionCount++;
        if (retriesLeft > 0) {
            retriesLeft -= 1;
        }
        lastExecutedDate = System.currentTimeMillis();
    }
    
    boolean shouldWeRetry() {
        return (!executed || (hasError() && !hasHardError())) && retriesLeft > 0;
    }

    long getItemId() {
        return itemId;
    }

    void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public long getLastExecutedDate() {
        return lastExecutedDate;
    }
}
