/* 
 * Copyright (C) 2012-2013 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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

package org.andstatus.app.data;

import java.util.Arrays;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.Origin;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;

/**
 * Database provider for the MyDatabase database.
 * 
 * The code of this application accesses this class through {@link android.content.ContentResolver}.
 * ContentResolver in it's turn accesses this class.
 * 
 */
public class MyProvider extends ContentProvider {
    static final String TAG = MyProvider.class.getSimpleName();
    
    /**
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        MyContextHolder.storeContextIfNotPresent(getContext(), this);
        return MyContextHolder.get().isReady();
    }

    /**
     * Get MIME type of the content, used for the supplied Uri
     * For discussion how this may be used see:
     * <a href="http://stackoverflow.com/questions/5351669/why-use-contentprovider-gettype-to-get-mime-type">Why use ContentProvider.getType() to get MIME type</a>
     */
    @Override
    public String getType(Uri uri) {
        return MatchedUri.fromUri(uri).getMimeType();
    }

    /**
     * Delete a record(s) from the database.
     * 
     * @see android.content.ContentProvider#delete(android.net.Uri,
     *      java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        String sqlDesc = "";
        int count = 0;
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        switch (uriParser.matched()) {
            case MSG:
                db.beginTransaction();
                try {
                    // Delete all related records from MyDatabase.MsgOfUser for these messages
                    String selectionG = " EXISTS ("
                            + "SELECT * FROM " + Msg.TABLE_NAME + " WHERE ("
                            + Msg.TABLE_NAME + "." + BaseColumns._ID + "=" + MsgOfUser.TABLE_NAME + "." + MyDatabase.MsgOfUser.MSG_ID
                            + ") AND ("
                            + selection
                            + "))";
                    String descSuffix = "; args=" + Arrays.toString(selectionArgs);
                    sqlDesc = selectionG + descSuffix;
                    count = db.delete(MsgOfUser.TABLE_NAME, selectionG, selectionArgs);
                    // Now delete messages themselves
                    sqlDesc = selection + descSuffix;
                    count = db.delete(Msg.TABLE_NAME, selection, selectionArgs);
                    db.setTransactionSuccessful();
                } catch(Exception e) {
                    MyLog.d(TAG, "; SQL='" + sqlDesc + "'", e);
                } finally {
                    db.endTransaction();
                }
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(ParsedUri.TIMELINE_URI, null);
                }
                break;

            case USERS:
                count = db.delete(User.TABLE_NAME, selection, selectionArgs);
                break;

            case USER:
                // TODO: Delete related records also... 
                long userId = uriParser.getUserId();
                count = db.delete(User.TABLE_NAME, BaseColumns._ID + "=" + userId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }
        return count;
    }

    /**
     * Insert a new record into the database.
     * 
     * @see android.content.ContentProvider#insert(android.net.Uri,
     *      android.content.ContentValues)
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        ContentValues values;
        MsgOfUserValues msgOfUserValues = new MsgOfUserValues(0);
        FollowingUserValues followingUserValues = null;
        long accountUserId = 0;
        
        long rowId;
        Uri newUri = null;
        try {
            // 2010-07-21 yvolk: "now" is calculated exactly like it is in other
            // parts of the code
            Long now = System.currentTimeMillis();
            SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();

            String table;

            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }

            ParsedUri uriParser = ParsedUri.fromUri(uri);
            switch (uriParser.matched()) {
                case TIMELINE:
                    accountUserId = uriParser.getAccountUserId();
                    
                    table = Msg.TABLE_NAME;
                    /**
                     * Add default values for missed required fields
                     */
                    if (!values.containsKey(Msg.AUTHOR_ID) && values.containsKey(Msg.SENDER_ID)) {
                        values.put(Msg.AUTHOR_ID, values.get(Msg.SENDER_ID).toString());
                    }
                    if (!values.containsKey(Msg.BODY)) {
                        values.put(Msg.BODY, "");
                    }
                    if (!values.containsKey(Msg.VIA)) {
                        values.put(Msg.VIA, "");
                    }
                    values.put(Msg.INS_DATE, now);
                    
                    msgOfUserValues = MsgOfUserValues.valueOf(accountUserId, values);
                    break;
                    
                case ORIGIN:
                    table = Origin.TABLE_NAME;
                    break;

                case USER:
                    table = User.TABLE_NAME;
                    values.put(User.INS_DATE, now);
                    accountUserId = uriParser.getAccountUserId();
                    followingUserValues = FollowingUserValues.valueOf(accountUserId, 0, values);
                    break;
                    
                default:
                    throw new IllegalArgumentException(uriParser.toString());
            }

            rowId = db.insert(table, null, values);
            if (rowId == -1) {
                throw new SQLException("Failed to insert row into " + uri);
            } else if ( User.TABLE_NAME.equals(table)) {
                optionallyLoadAvatar(rowId, values);
            }
            
            msgOfUserValues.setMsgId(rowId);
            msgOfUserValues.insert(db);

            if (followingUserValues != null) {
                followingUserValues.followingUserId =  rowId;
                followingUserValues.update(db);
            }

            switch (uriParser.matched()) {
                case TIMELINE:
                    // The resulted Uri has several parameters...
                    newUri = ParsedUri.getTimelineMsgUri(accountUserId, TimelineTypeEnum.HOME , true, rowId);
                    break;
                case USER:
                    newUri = ParsedUri.getUserUri(accountUserId, rowId);
                    break;
                case ORIGIN:
                    newUri = ParsedUri.getOriginUri(rowId);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
          MyLog.e(this, "Insert " + uri, e);
        }
        return newUri;
    }

    private void optionallyLoadAvatar(long userId, ContentValues values) {
        if (MyPreferences.showAvatars() && values.containsKey(User.AVATAR_URL)) {
            AvatarData.newForUser(userId).requestDownload();
        }
    }
    
    /**
     * Get a cursor to the database
     * 
     * @see android.content.ContentProvider#query(android.net.Uri,
     *      java.lang.String[], java.lang.String, java.lang.String[],
     *      java.lang.String)
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selectionIn, String[] selectionArgsIn,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        boolean built = false;
        String selection = selectionIn;
        String[] selectionArgs = selectionArgsIn; 
        String sql = "";

        ParsedUri uriParser = ParsedUri.fromUri(uri);
        switch (uriParser.matched()) {
            case TIMELINE:
                qb.setDistinct(true);
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.MSG);
                break;

            case MSG_COUNT:
                sql = "SELECT count(*) FROM " + Msg.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS;
                if (!TextUtils.isEmpty(selection)) {
                    sql += " WHERE " + selection;
                }
                break;

            case TIMELINE_MSG_ID:
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.MSG);
                qb.appendWhere(ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID + "=" + uriParser.getMessageId());
                break;

            case TIMELINE_SEARCH:
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.MSG);
                String s1 = uri.getLastPathSegment();
                if (s1 != null) {
                    if (!TextUtils.isEmpty(selection)) {
                        selection = " AND (" + selection + ")";
                    } else {
                        selection = "";
                    }
                    // TODO: Search in MyDatabase.User.USERNAME also
                    selection = "(" + User.AUTHOR_NAME + " LIKE ?  OR " + Msg.BODY
                            + " LIKE ?)" + selection;

                    selectionArgs = addBeforeArray(selectionArgs, "%" + s1 + "%");
                    selectionArgs = addBeforeArray(selectionArgs, "%" + s1 + "%");
                }
                break;

            case MSG:
                qb.setTables(Msg.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS);
                qb.setProjectionMap(ProjectionMap.MSG);
                break;

            case USERS:
                qb.setTables(User.TABLE_NAME);
                qb.setProjectionMap(ProjectionMap.USER);
                break;

            case USER:
                qb.setTables(User.TABLE_NAME);
                qb.setProjectionMap(ProjectionMap.USER);
                qb.appendWhere(BaseColumns._ID + "=" + uriParser.getUserId());
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            switch (uriParser.matched()) {
                case TIMELINE:
                case TIMELINE_MSG_ID:
                    orderBy = Msg.DEFAULT_SORT_ORDER;
                    break;

                case MSG_COUNT:
                    orderBy = "";
                    break;

                case USERS:
                case USER:
                    orderBy = User.DEFAULT_SORT_ORDER;
                    break;

                default:
                    throw new IllegalArgumentException(uriParser.toString());
            }
        } else {
            orderBy = sortOrder;
        }

        Cursor c = null;
        if (MyContextHolder.get().isReady()) {
            // Get the database and run the query
            SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
            boolean logQuery = MyLog.isVerboseEnabled();
            try {
                if (sql.length() == 0) {
                    sql = qb.buildQuery(projection, selection, null, null, orderBy, null);
                    built = true;
                }
                // Here we substitute ?-s in selection with values from selectionArgs
                c = db.rawQuery(sql, selectionArgs);
            } catch (Exception e) {
                logQuery = true;
                MyLog.e(this, "Database query failed", e);
            }

            if (logQuery) {
                String msg = "query, SQL=\"" + sql + "\"";
                if (selectionArgs != null && selectionArgs.length > 0) {
                    msg += "; selectionArgs=" + Arrays.toString(selectionArgs);
                }
                MyLog.v(TAG, msg);
                if (built) {
                    msg = "uri=" + uri + "; projection=" + Arrays.toString(projection)
                    + "; selection=" + selection + "; sortOrder=" + sortOrder
                    + "; qb.getTables=" + qb.getTables() + "; orderBy=" + orderBy;
                    MyLog.v(TAG, msg);
                }
            }
        }

        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    private String[] addBeforeArray(String[] array, String s) {
        int length = 0;
        if (array != null) {
            length = array.length;
        }
        String[] ans = new String[length + 1];
        if (length > 0) {
            System.arraycopy(array, 0, ans, 1, array.length);
        }
        ans[0] = s;
        return ans;
    }

    /**
     * Update objects (one or several records) in the database
     * 
     * @see android.content.ContentProvider#update(android.net.Uri,
     *      android.content.ContentValues, java.lang.String, java.lang.String[])
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        int count = 0;
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        long accountUserId = 0;
        switch (uriParser.matched()) {
            case MSG:
                count = db.update(Msg.TABLE_NAME, values, selection, selectionArgs);
                break;

            case TIMELINE_MSG_ID:
                accountUserId = uriParser.getAccountUserId();
                long rowId = uriParser.getMessageId();
                MsgOfUserValues msgOfUserValues = MsgOfUserValues.valueOf(accountUserId, values);
                msgOfUserValues.setMsgId(rowId);
                if (values.size() > 0) {
                    count = db.update(Msg.TABLE_NAME, values, BaseColumns._ID + "=" + rowId
                            + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                count += msgOfUserValues.update(db);
                break;

            case USERS:
                count = db.update(User.TABLE_NAME, values, selection, selectionArgs);
                break;
            case USER:
                accountUserId = uriParser.getAccountUserId();
                long selectedUserId = uriParser.getUserId();
                FollowingUserValues followingUserValues = FollowingUserValues.valueOf(accountUserId, selectedUserId, values);
                count = db.update(User.TABLE_NAME, values, BaseColumns._ID + "=" + selectedUserId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                followingUserValues.update(db);
                optionallyLoadAvatar(selectedUserId, values);
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        return count;
    }
}
