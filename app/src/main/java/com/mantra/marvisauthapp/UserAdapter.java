package com.mantra.marvisauthapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<IrisUser> userList;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onEditClick(IrisUser user);
        void onDeleteClick(IrisUser user);
    }

    public UserAdapter(List<IrisUser> userList, OnUserClickListener listener) {
        this.userList = userList;
        this.listener = listener;
    }

    public void updateList(List<IrisUser> newList) {
        this.userList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        IrisUser user = userList.get(position);

        holder.txtUserName.setText(user.name);
        holder.txtUserId.setText("ID: " + user.id);

        String leftStatus = user.hasLeftEye ? "✅" : "❌";
        String rightStatus = user.hasRightEye ? "✅" : "❌";
        holder.txtEnrolledEyes.setText("Left " + leftStatus + " | Right " + rightStatus);

        holder.btnEditUser.setOnClickListener(v -> listener.onEditClick(user));
        holder.btnDeleteUser.setOnClickListener(v -> listener.onDeleteClick(user));
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView txtUserName, txtUserId, txtEnrolledEyes;
        ImageView btnEditUser, btnDeleteUser;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            txtUserName = itemView.findViewById(R.id.txtUserName);
            txtUserId = itemView.findViewById(R.id.txtUserId);
            txtEnrolledEyes = itemView.findViewById(R.id.txtEnrolledEyes);
            btnEditUser = itemView.findViewById(R.id.btnEditUser);
            btnDeleteUser = itemView.findViewById(R.id.btnDeleteUser);
        }
    }
}