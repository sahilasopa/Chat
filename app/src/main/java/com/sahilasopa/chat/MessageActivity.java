package com.sahilasopa.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sahilasopa.chat.Adapters.MessageAdapter;
import com.sahilasopa.chat.Models.Chat;
import com.sahilasopa.chat.databinding.ActivityMessageBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MessageActivity extends AppCompatActivity {
    private MessageAdapter messageAdapter;
    private List<Chat> chats;
    long timestamp;
    ActivityMessageBinding binding;
    Chat chat = new Chat();
    DatabaseReference reference;
    DatabaseReference referenceSeen;
    FirebaseAuth auth;
    FirebaseUser firebaseUser;
    RecyclerView recyclerView;
    ValueEventListener seenListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivityMessageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        auth = FirebaseAuth.getInstance();
        setTitle(getIntent().getExtras().get("username").toString());
        if (auth.getCurrentUser() == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("status", "offline");
        reference = FirebaseDatabase.getInstance().getReference().child("Users").child(auth.getCurrentUser().getUid());
        reference.onDisconnect().updateChildren(hashMap);
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(boolean.class); // Warning but i cant find any fix :(
                if (connected) {
                    status("online");
                } else {
                    System.out.println("not connected");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                System.err.println("Listener was cancelled");
            }
        });
        chats = new ArrayList<>();
        recyclerView = binding.chats;
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        binding.editMessage.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        if (!(binding.editMessage.getText().toString().isEmpty())) {
                            sendMessage();
                        }
                        return true;
                    default:
                        break;
                }
            }
            return false;
        });
        binding.buttonSend.setOnClickListener(v -> {
            if (!(binding.editMessage.getText().toString().isEmpty())) {
                sendMessage();
            }
        });
        getMessage();
        seenMessage(getIntent().getExtras().get("user").toString());
    }

    public void getMessage() {
        reference = FirebaseDatabase.getInstance().getReference("Chats");
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chats.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Chat chat = ds.getValue(Chat.class);
                    assert chat != null;
                    assert firebaseUser != null;
                    if (chat.getReceiver().equals(firebaseUser.getUid()) && chat.getSender().equals(getIntent().getExtras().get("user").toString()) ||
                            chat.getReceiver().equals(getIntent().getExtras().get("user").toString()) && chat.getSender().equals(firebaseUser.getUid())) {
                        timestamp = System.currentTimeMillis();
                        chats.add(chat);
                    }
                }
                messageAdapter = new MessageAdapter(getApplicationContext(), chats);
                recyclerView.setAdapter(messageAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    public void sendMessage() {
        reference = FirebaseDatabase.getInstance().getReference("Chats");
        timestamp = System.currentTimeMillis();
        assert firebaseUser != null;
        chat.setTimestamp(timestamp);
        chat.setSender(firebaseUser.getUid());
        chat.setReceiver(getIntent().getExtras().get("user").toString());
        chat.setMessage(binding.editMessage.getText().toString().trim().replaceAll("\\s+", " "));
        chat.setSeen(false);
        binding.editMessage.setText("");
        binding.editMessage.requestFocus();
        reference.push().setValue(chat);
    }

    public void status(String status) {
        if (auth.getCurrentUser() != null) {
            reference = FirebaseDatabase.getInstance().getReference().child("Users").child(auth.getCurrentUser().getUid());
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("status", status);
            reference.updateChildren(hashMap);
        }
    }

    public void seenMessage(final String userId) {
        referenceSeen = FirebaseDatabase.getInstance().getReference("Chats");
        seenListener = referenceSeen.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Chat chat = dataSnapshot.getValue(Chat.class);
                    assert chat != null;
                    if (chat.getReceiver().equals(firebaseUser.getUid()) && chat.getSender().equals(userId)) {
                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("seen", true);
                        dataSnapshot.getRef().updateChildren(hashMap);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        status("online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        referenceSeen.removeEventListener(seenListener);
        status("offline");
    }

}