package com.mantra.marvisauthapp;

import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UserListActivity extends AppCompatActivity {

    private IrisDatabaseHelper dbHelper;
    private RecyclerView recyclerView;
    private TextView txtEmptyState;
    private UserAdapter adapter;
    private List<IrisUser> userList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        dbHelper = new IrisDatabaseHelper(this);

        recyclerView = findViewById(R.id.recyclerViewUsers);
        txtEmptyState = findViewById(R.id.txtEmptyState);
        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new UserAdapter(userList, new UserAdapter.OnUserClickListener() {
            @Override
            public void onEditClick(IrisUser user) {
                showEditDialog(user);
            }

            @Override
            public void onDeleteClick(IrisUser user) {
                showDeleteDialog(user);
            }
        });
        recyclerView.setAdapter(adapter);

        loadUsers();
    }

    private void loadUsers() {
        userList.clear();
        Cursor cursor = dbHelper.getLightweightUsers(); // Use lightweight query

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(IrisDatabaseHelper.COL_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(IrisDatabaseHelper.COL_NAME));

                // SQLite evaluates IS NOT NULL as 1 (true) or 0 (false)
                boolean hasLeft = cursor.getInt(cursor.getColumnIndexOrThrow("has_left")) == 1;
                boolean hasRight = cursor.getInt(cursor.getColumnIndexOrThrow("has_right")) == 1;

                userList.add(new IrisUser(id, name, hasLeft, hasRight));
            } while (cursor.moveToNext());
        }
        cursor.close();

        adapter.updateList(userList);

        if (userList.isEmpty()) {
            txtEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            txtEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showEditDialog(IrisUser user) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_user, null);
        EditText edtEditName = view.findViewById(R.id.edtEditName);
        edtEditName.setText(user.name);
        // Move cursor to the end of the text
        edtEditName.setSelection(user.name.length());

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("SAVE", (dialog, which) -> {
                    String newName = edtEditName.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        if (dbHelper.updateUserName(user.id, newName)) {
                            Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show();
                            loadUsers(); // Refresh list
                        } else {
                            Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showDeleteDialog(IrisUser user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete '" + user.name + "'? This action cannot be undone.")
                .setPositiveButton("DELETE", (dialog, which) -> {
                    if (dbHelper.deleteUser(user.id)) {

                        // Delete the local files associated with this user
                        deleteUserFilesLocally(user.id);

                        Toast.makeText(this, "User deleted", Toast.LENGTH_SHORT).show();
                        loadUsers(); // Refresh list
                    } else {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    // Helper method to remove the user's specific image directory
    private void deleteUserFilesLocally(long userId) {
        File baseDirectory = new File(getExternalFilesDir(null), "MarvisUsers");
        File userDirectory = new File(baseDirectory, "user_" + userId);

        if (userDirectory.exists() && userDirectory.isDirectory()) {
            // In Java, a directory must be empty before it can be deleted.
            // Loop through and delete the left/right .bmp files first.
            File[] files = userDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            // Now delete the empty user directory
            userDirectory.delete();
        }
    }
}