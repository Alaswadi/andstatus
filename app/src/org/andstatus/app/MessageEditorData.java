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

package org.andstatus.app;

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

class MessageEditorData {
    String messageText = "";
    Uri mediaUri = Uri.EMPTY;
    /**
     * Id of the Message to which we are replying
     * -1 - is non-existent id
     */
    long inReplyToId = 0;
    boolean mReplyAll = false; 
    long recipientId = 0;
    MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");

    public MessageEditorData() {
        this(null);
    }
    
    public MessageEditorData(MyAccount myAccount) {
        if (myAccount == null) {
            ma = MyAccount.getEmpty(MyContextHolder.get(), "");
        } else {
            ma = myAccount;
        }
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((ma == null) ? 0 : ma.hashCode());
        result = prime * result + ((mediaUri == null) ? 0 : mediaUri.hashCode());
        result = prime * result + ((messageText == null) ? 0 : messageText.hashCode());
        result = prime * result + (int) (recipientId ^ (recipientId >>> 32));
        result = prime * result + (int) (inReplyToId ^ (inReplyToId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MessageEditorData other = (MessageEditorData) obj;
        if (ma == null) {
            if (other.ma != null)
                return false;
        } else if (!ma.equals(other.ma))
            return false;
        if (mediaUri == null) {
            if (other.mediaUri != null)
                return false;
        } else if (!mediaUri.equals(other.mediaUri))
            return false;
        if (messageText == null) {
            if (other.messageText != null)
                return false;
        } else if (!messageText.equals(other.messageText))
            return false;
        if (recipientId != other.recipientId)
            return false;
        if (inReplyToId != other.inReplyToId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MessageEditorData [messageText=");
        builder.append(messageText);
        builder.append(", mediaUri=");
        builder.append(mediaUri);
        builder.append(", inReplyToId=");
        builder.append(inReplyToId);
        builder.append(", mReplyAll=");
        builder.append(mReplyAll);
        builder.append(", recipientId=");
        builder.append(recipientId);
        builder.append(", ma=");
        builder.append(ma);
        builder.append("]");
        return builder.toString();
    }
    
    public void save(SharedPreferences.Editor outState) {
        if (outState != null) {
            outState.putString(IntentExtra.EXTRA_MESSAGE_TEXT.key, messageText);
            outState.putString(IntentExtra.EXTRA_MEDIA_URI.key, mediaUri.toString());
            outState.putLong(IntentExtra.EXTRA_INREPLYTOID.key, inReplyToId);
            outState.putLong(IntentExtra.EXTRA_RECIPIENTID.key, recipientId);
            outState.putString(IntentExtra.EXTRA_ACCOUNT_NAME.key, getMyAccount().getAccountName());
        }
    }
    
    static MessageEditorData load(SharedPreferences savedState) {
        if (savedState != null 
                && savedState.contains(IntentExtra.EXTRA_INREPLYTOID.key) 
                && savedState.contains(IntentExtra.EXTRA_MESSAGE_TEXT.key)) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(
                    savedState.getString(IntentExtra.EXTRA_ACCOUNT_NAME.key, ""));
            MessageEditorData data = new MessageEditorData(ma);
            String messageText = savedState.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key, "");
            Uri mediaUri = UriUtils.fromString(savedState.getString(IntentExtra.EXTRA_MEDIA_URI.key, ""));
            if (!TextUtils.isEmpty(messageText) || !UriUtils.isEmpty(mediaUri)) {
                data.messageText = messageText;
                data.mediaUri = mediaUri;
                data.inReplyToId = savedState.getLong(IntentExtra.EXTRA_INREPLYTOID.key, 0);
                data.recipientId = savedState.getLong(IntentExtra.EXTRA_RECIPIENTID.key, 0);
            }
            return data;
        } else {
            return new MessageEditorData();
        }
    }

    MyAccount getMyAccount() {
        return ma;
    }
    
    boolean isEmpty() {
        return TextUtils.isEmpty(messageText) && UriUtils.isEmpty(mediaUri);
    }

    public MessageEditorData setMessageText(String textInitial) {
        messageText = textInitial;
        return this;
    }

    public MessageEditorData setMediaUri(Uri mediaUri) {
        this.mediaUri = UriUtils.notNull(mediaUri);
        return this;
    }
    
    public MessageEditorData setInReplyToId(long msgId) {
        inReplyToId = msgId;
        return this;
    }
    
    public MessageEditorData setReplyAll(boolean replyAll) {
        mReplyAll = replyAll;
        return this;
    }

    public MessageEditorData addMentionsToText() {
        if (inReplyToId != 0) {
            if (mReplyAll) {
                addConversationMembersToText();
            } else {
                addMentionedAuthorOfMessageToText(inReplyToId);
            }
        }
        return this;
    }
    
    private void addConversationMembersToText() {
        if (!ma.isValid()) {
            return;
        }
        ConversationLoader<ConversationMemberItem> loader = new ConversationLoader<ConversationMemberItem>(
                ConversationMemberItem.class,
                MyContextHolder.get().context(), ma, inReplyToId);
        loader.load(null);
        List<Long> mentioned = new ArrayList<Long>();
        mentioned.add(ma.getUserId());
        for(ConversationMemberItem item : loader.getMsgs()) {
            if (!mentioned.contains(item.authorId)) {
                addMentionedUserToText(item.authorId);
                mentioned.add(item.authorId);
            }
        }
    }

    public MessageEditorData addMentionedUserToText(long mentionedUserId) {
        String name = MyQuery.userIdToName(mentionedUserId, getUserInTimeline());
        addMetionedUsernameToText(name);
        return this;
    }

    private UserInTimeline getUserInTimeline() {
        return ma
                .getOrigin().isMentionAsWebFingerId() ? UserInTimeline.WEBFINGER_ID
                : UserInTimeline.USERNAME;
    }

    private void addMentionedAuthorOfMessageToText(long messageId) {
        String name = MyQuery.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, messageId, getUserInTimeline());
        addMetionedUsernameToText(name);
    }
    
    private void addMetionedUsernameToText(String name) {
        if (!TextUtils.isEmpty(name)) {
            String messageText1 = "@" + name + " ";
            if (!TextUtils.isEmpty(messageText)) {
                messageText1 += messageText;
            }
            messageText = messageText1;
        }
    }

    public MessageEditorData setRecipientId(long userId) {
        recipientId = userId;
        return this;
    }

    public boolean sameContext(MessageEditorData dataIn) {
        return inReplyToId == dataIn.inReplyToId
                && recipientId == dataIn.recipientId
                && getMyAccount().getAccountName()
                        .compareTo(dataIn.getMyAccount().getAccountName()) == 0
                && mReplyAll == dataIn.mReplyAll;
    }
}
