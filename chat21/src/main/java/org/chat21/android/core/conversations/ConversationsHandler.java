package org.chat21.android.core.conversations;

import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.chat21.android.core.ChatManager;
import org.chat21.android.core.conversations.listeners.ConversationsListener;
import org.chat21.android.core.conversations.models.Conversation;
import org.chat21.android.core.exception.ChatRuntimeException;
import org.chat21.android.utils.StringUtils;

/**
 * Created by andrealeo on 18/12/17.
 */

public class ConversationsHandler {
    private static final String TAG = ConversationsHandler.class.getName();

    private List<Conversation> conversations;
    private DatabaseReference conversationsNode;
    private String appId;
    private String currentUserId;
    private List<ConversationsListener> conversationsListeners;
    private ChildEventListener conversationsChildEventListener;
    private Comparator<Conversation> conversationComparator;

    private String currentOpenConversationId;

    public ConversationsHandler(String firebaseUrl, String appId, String currentUserId) {
        conversationsListeners = new ArrayList<ConversationsListener>();
        conversations = new ArrayList<>(); // conversations in memory

        this.appId = appId;
        this.currentUserId = currentUserId;

        if (StringUtils.isValid(firebaseUrl)) {
            this.conversationsNode = FirebaseDatabase.getInstance()
                    .getReferenceFromUrl(firebaseUrl)
                    .child("/apps/" + appId + "/users/" + currentUserId + "/conversations/");
        } else {
            this.conversationsNode = FirebaseDatabase.getInstance()
                    .getReference()
                    .child("/apps/" + appId + "/users/" + currentUserId + "/conversations/");
        }
        this.conversationsNode.keepSynced(true);

        conversationComparator = new Comparator<Conversation>() {
            @Override
            public int compare(Conversation o1, Conversation o2) {
                try {
                    return o2.getTimestampLong().compareTo(o1.getTimestampLong());
                } catch (Exception e) {
                    Log.e(TAG, "ConversationHandler.sortConversationsInMemory: " +
                            "cannot compare conversations timestamp", e);
                    return 0;
                }
            }
        };

//        Log.d(TAG, "ConversationsHandler.conversationsNode == " + conversationsNode.toString());
    }

    public ChildEventListener connect(ConversationsListener conversationsListener) {
        this.upsertConversationsListener(conversationsListener);
        return connect();
    }

    public ChildEventListener connect() {

        if (this.conversationsChildEventListener == null) {

            this.conversationsChildEventListener = conversationsNode.addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
                    Log.d(TAG, "ConversationsHandler.connect.onChildAdded");

                    try {
                        Conversation conversation = decodeConversationFromSnapshot(dataSnapshot);

                        // it sets the conversation as read if the person whom are talking to is the current user
                        if (currentUserId.equals(conversation.getSender())) {
                            setConversationRead(conversation.getConversationId());
                        }

                        addConversation(conversation);
                    } catch (Exception e) {
                        notifyConversationAdded(null, new ChatRuntimeException(e));
                    }

//                    try {
//                        Conversation conversation = decodeConversationFromSnapshot(dataSnapshot);
//
//                        // it sets the conversation as read if the person whom are talking to is the current user
//                        if (currentUserId.equals(conversation.getSender())) {
//                            setConversationRead(conversation.getConversationId());
//                        }
//
//                        saveOrUpdateConversationInMemory(conversation);
//                        sortConversationsInMemory();
//
//                        if (conversationsListeners != null) {
//                            for (ConversationsListener conversationsListener : conversationsListeners) {
//                                conversationsListener.onConversationAdded(conversation, null);
//                            }
//                        }
//
//                    } catch (Exception e) {
//                        if (conversationsListeners != null) {
//                            for (ConversationsListener conversationsListener : conversationsListeners) {
//                                conversationsListener.onConversationAdded(null, new ChatRuntimeException(e));
//                            }
//                        }
//                    }
                }

                //for return receipt
                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {
                    Log.d(TAG, "observeMessages.onChildChanged");

                    try {
                        Conversation conversation = decodeConversationFromSnapshot(dataSnapshot);
                        updateConversation(conversation);
                    } catch (Exception e) {
                        notifyConversationChanged(null, new ChatRuntimeException(e));
                    }

//                    try {
//                        Conversation conversation = decodeConversationFromSnapshot(dataSnapshot);
//
//                        saveOrUpdateConversationInMemory(conversation);
//                        sortConversationsInMemory();
//
//                        if (conversationsListeners != null) {
//                            for (ConversationsListener conversationsListener : conversationsListeners) {
//                                conversationsListener.onConversationChanged(conversation, null);
//                            }
//                        }
//
//                    } catch (Exception e) {
//                        if (conversationsListeners != null) {
//                            for (ConversationsListener conversationsListener : conversationsListeners) {
//                                conversationsListener.onConversationChanged(null, new ChatRuntimeException(e));
//                            }
//                        }
//                    }
                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {
                    Log.d(TAG, "observeMessages.onChildRemoved");

//                Log.d(TAG, "observeMessages.onChildRemoved: dataSnapshot == " + dataSnapshot.toString());

//                try {
//                    Conversation conversation = decodeGroupFromSnapshot(dataSnapshot);
//
//                    deleteConversationFromMemory(conversation);
//                    sortConversationsInMemory();
//
//                    for (ConversationsListener conversationsListener : conversationsListeners) {
//                        conversationsListener.onConversationRemoved(null);
//                    }
//
//                } catch (Exception e) {
//                    for (ConversationsListener conversationsListener : conversationsListeners) {
//                        conversationsListener.onConversationRemoved(new ChatRuntimeException(e));
//                    }
//                }
                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {
//                Log.d(TAG, "observeMessages.onChildMoved");
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
//                Log.d(TAG, "observeMessages.onCancelled");

                }
            });
        } else {
            Log.i(TAG, "already connected : ");
        }

        return conversationsChildEventListener;
    }

    public String getCurrentOpenConversationId() {
        return currentOpenConversationId;
    }

    public void setCurrentOpenConversationId(String currentOpenConversationId) {
        this.currentOpenConversationId = currentOpenConversationId;
    }

    public List<Conversation> getConversations() {
        sortConversationsInMemory(); // ensure to return a sorted list
        return conversations;
    }

    // it checks if the conversation already exists.
    // if the conversation exists update it, add it otherwise
    private void saveOrUpdateConversationInMemory(Conversation newConversation) {

        // look for the conversation
        int index = -1;
        for (Conversation tempConversation : conversations) {
            if (tempConversation.equals(newConversation)) {
                index = conversations.indexOf(tempConversation);
                break;
            }
        }

        if (index != -1) {
            // conversation already exists
            conversations.set(index, newConversation); // update the existing conversation
        } else {
            // conversation not exists
            conversations.add(newConversation); // insert a new conversation
        }
    }

    // it checks if the conversation already exists.
    // if the conversation exists delete it
    private void deleteConversationFromMemory(Conversation conversationToDelete) {
        // look for the conversation
        int index = -1;
        for (Conversation tempConversation : conversations) {
            if (tempConversation.equals(conversationToDelete)) {
                index = conversations.indexOf(tempConversation);
                break;
            }
        }

        if (index != -1) {
            // conversation already exists
            conversations.remove(index); // delete existing conversation
        }
    }

    private void sortConversationsInMemory() {
        Log.d(TAG, "ConversationHandler.sortConversationsInMemory");

        // check if the list has al least 1 item.
        // 1 item is already sorted
        if (conversations.size() > 1) {
            Collections.sort(conversations, conversationComparator);
        }
    }

    public void addConversation(Conversation conversation) {

        try {
            saveOrUpdateConversationInMemory(conversation);
            sortConversationsInMemory();
            notifyConversationAdded(conversation, null);
        } catch (Exception e) {
            notifyConversationAdded(null, new ChatRuntimeException(e));
        }
    }

    public void updateConversation(Conversation conversation) {

        try {
            saveOrUpdateConversationInMemory(conversation);
            sortConversationsInMemory();
            notifyConversationChanged(conversation, null);
        } catch (Exception e) {
            notifyConversationChanged(null, new ChatRuntimeException(e));
        }
    }

    private void notifyConversationAdded(Conversation conversation, ChatRuntimeException exception) {
        if (conversationsListeners != null) {
            for (ConversationsListener conversationsListener : conversationsListeners) {
                conversationsListener.onConversationAdded(conversation, exception);
            }
        }
    }

    private void notifyConversationChanged(Conversation conversation, ChatRuntimeException exception) {
        if (conversationsListeners != null) {
            for (ConversationsListener conversationsListener : conversationsListeners) {
                conversationsListener.onConversationChanged(conversation, exception);
            }
        }
    }

    public static Conversation decodeConversationFromSnapshot(DataSnapshot dataSnapshot) {
        Log.d(TAG, "ConversationHandler.decodeConversationFromSnapshop");

        Conversation conversation = new Conversation();

        // conversationId
        conversation.setConversationId(dataSnapshot.getKey());
        Log.d(TAG, "ConversationsHandler.decodeConversationSnapshop: conversationId = " +
                conversation.getConversationId());

        Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();

        // is_new
        try {
            boolean is_new = (boolean) map.get("is_new");
            conversation.setIs_new(is_new);
        } catch (Exception e) {
            Log.e(TAG, "ConversationsHandler.decodeConversationSnapshop:" +
                    " cannot retrieve is_new");
        }

        // last_message_text
        try {
            String last_message_text = (String) map.get("last_message_text");
            conversation.setLast_message_text(last_message_text);
        } catch (Exception e) {
            Log.e(TAG, "ConversationsHandler.decodeConversationSnapshop: " +
                    "cannot retrieve last_message_text");
        }

        // recipient
        try {
            String recipient = (String) map.get("recipient");
            conversation.setRecipient(recipient);
        } catch (Exception e) {
            Log.e(TAG, "ConversationsHandler.decodeConversationSnapshop:" +
                    " cannot retrieve recipient");
        }

        // rrecipient_fullname
        try {
            String recipientFullName = (String) map.get("recipient_fullname");
            conversation.setRecipientFullName(recipientFullName);
        } catch (Exception e) {
            Log.e(TAG, "cannot retrieve recipient_fullname");
        }

        // sender
        try {
            String sender = (String) map.get("sender");
            conversation.setSender(sender);
        } catch (Exception e) {
            Log.e(TAG, "cannot retrieve sender");
        }

        // sender_fullname
        try {
            String sender_fullname = (String) map.get("sender_fullname");
            conversation.setSender_fullname(sender_fullname);
        } catch (Exception e) {
            Log.e(TAG, "cannot retrieve sender_fullname");
        }

        // status
        try {
            long status = (long) map.get("status");
            conversation.setStatus((int) status);
        } catch (Exception e) {
            Log.e(TAG, "cannot retrieve status");
        }

        // timestamp
        try {
            long timestamp = (long) map.get("timestamp");
            conversation.setTimestamp(timestamp);
        } catch (Exception e) {
            Log.e(TAG, "cannot retrieve timestamp");
        }

        try {
            String channelType = (String) map.get("channel_type");
            conversation.setChannelType(channelType);
        } catch (Exception e) {
            Log.e(TAG, "cannot retrieve channel_type");
        }


        // convers with
        if (conversation.getRecipient()
                .equals(ChatManager.getInstance().getLoggedUser().getId())) {
            conversation.setConvers_with(conversation.getSender());
            conversation.setConvers_with_fullname(conversation.getSender_fullname());
        } else {
            conversation.setConvers_with(conversation.getRecipient());
            conversation.setConvers_with_fullname(conversation.getRecipientFullName());
        }

        return conversation;
    }


    public void setConversationRead(final String recipientId) {
        Log.d(TAG, "setConversationRead");

        Conversation conversation = getById(recipientId);
        // check if the conversation is new
        // if it is new set the conversation as read (false), do nothing otherwise
        if (conversation != null && conversation.getIs_new()) {
            conversationsNode.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    // check if the conversation exists to prevent conversation with only "is_new" value
                    if (snapshot.hasChild(recipientId)) {
                        // update the state
                        conversationsNode.child(recipientId)
                                .child("is_new")
                                .setValue(false); // the conversation has been read
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    String errorMessage = "cannot mark the conversation as read: " +
                            databaseError.getMessage();
                    Log.e(TAG, errorMessage);
                    FirebaseCrash.report(new Exception(errorMessage));
                }
            });
        }
    }

    public List<ConversationsListener> getConversationsListener() {
        return conversationsListeners;
    }

//    public void setConversationsListener(List<ConversationsListener> conversationsListeners) {
//        this.conversationsListeners = conversationsListeners;
//    }

    public void addConversationsListener(ConversationsListener conversationsListener) {
        Log.v(TAG, "  addGroupsListener called");

        this.conversationsListeners.add(conversationsListener);

        Log.i(TAG, "  conversationsListener with hashCode: " +
                conversationsListener.hashCode() + " added");
    }

    public void removeConversationsListener(ConversationsListener conversationsListener) {
        Log.v(TAG, "  removeGroupsListener called");

        if (conversationsListeners != null)
            this.conversationsListeners.remove(conversationsListener);

        Log.i(TAG, "  conversationsListener with hashCode: " +
                conversationsListener.hashCode() + " removed");
    }

    public void upsertConversationsListener(ConversationsListener conversationsListener) {
        Log.v(TAG, "  upsertGroupsListener called");

        if (conversations.contains(conversationsListener)) {
            this.removeConversationsListener(conversationsListener);
            this.addConversationsListener(conversationsListener);
            Log.i(TAG, "  conversationsListener with hashCode: " +
                    conversationsListener.hashCode() + " updated");

        } else {
            this.addConversationsListener(conversationsListener);
            Log.i(TAG, "  conversationsListener with hashCode: " +
                    conversationsListener.hashCode() + " added");
        }
    }

    public void removeAllConversationsListeners() {
        this.conversationsListeners = null;
        Log.i(TAG, "Removed all ConversationsListeners");
    }

    public ChildEventListener getConversationsChildEventListener() {
        return conversationsChildEventListener;
    }

    public void disconnect() {
        this.conversationsNode.removeEventListener(this.conversationsChildEventListener);
        this.removeAllConversationsListeners();
    }

//    public void deleteConversation(String recipientId, final ConversationsListener conversationsListener) {
//        DatabaseReference.CompletionListener onConversationRemoved
//                = new DatabaseReference.CompletionListener() {
//            @Override
//            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
//
//                if (databaseError == null) {
//                    // conversation deleted with success
//                    conversationsListener.onConversationRemoved(null);
//                } else {
//                    // there are error
//                    // conversation not deleted
//                    conversationsListener.onConversationRemoved(new ChatRuntimeException(databaseError.toException()));
//                }
//            }
//        };
//
//        // remove the conversation with recipientId
//        this.conversationsNode.child(recipientId).removeValue(onConversationRemoved);
//    }

//    public DatabaseReference getConversationsNode() {
//        return conversationsNode;
//    }


    /**
     * It looks for the conversation with {@code conversationId}
     *
     * @param conversationId the group id to looking for
     * @return the conversation if exists, null otherwise
     */
    public Conversation getById(String conversationId) {
        for (Conversation conversation : conversations) {
            if (conversation.getConversationId().equals(conversationId)) {
                return conversation;
            }
        }
        return null;
    }
}
