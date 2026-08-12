package com.example.bunnycare;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class RabbitAdapter extends RecyclerView.Adapter<RabbitAdapter.RabbitViewHolder> {

    public interface OnRabbitItemListener {
        void onRabbitTap(Rabbit rabbit, View anchorView);
        void onRabbitLongPress(Rabbit rabbit);
    }

    private static final String COLOR_HEALTHY = "#4CAF50";
    private static final String COLOR_ATTENTION = "#D9534F";

    private final Context context;
    private final List<Rabbit> rabbitList;
    private final OnRabbitItemListener listener;

    public RabbitAdapter(
            Context context,
            List<Rabbit> rabbitList,
            OnRabbitItemListener listener
    ) {
        this.context = context;
        this.rabbitList = rabbitList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RabbitViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_rabbit_card, parent, false);

        return new RabbitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RabbitViewHolder holder,
            int position
    ) {
        Rabbit rabbit = rabbitList.get(position);

        String name = rabbit.getRabbitName();

        if (name == null || name.trim().isEmpty()) {
            name = "Unnamed rabbit";
        }

        holder.txtRabbitName.setText(name);
        holder.txtRabbitBreedAge.setText(rabbit.getBreedAgeLabel());

        if (rabbit.getImageUrl() != null
                && !rabbit.getImageUrl().isEmpty()) {

            Glide.with(context)
                    .load(rabbit.getImageUrl())
                    .placeholder(R.drawable.rabbit_thumb_bg)
                    .error(R.drawable.rabbit_thumb_bg)
                    .into(holder.imgRabbit);

        } else {

            holder.imgRabbit.setImageDrawable(null);
            holder.imgRabbit.setBackgroundResource(
                    R.drawable.rabbit_thumb_bg
            );
        }

        if (holder.statusDot.getBackground() != null) {
            holder.statusDot.getBackground().setTint(
                    Color.parseColor(
                            rabbit.isHealthy()
                                    ? COLOR_HEALTHY
                                    : COLOR_ATTENTION
                    )
            );
        }

        holder.imgQrBadge.setVisibility(
                rabbit.isHasQr()
                        ? View.VISIBLE
                        : View.GONE
        );

        holder.itemView.setOnClickListener(v -> {

            if (listener != null) {
                listener.onRabbitTap(
                        rabbit,
                        holder.itemView
                );
            }
        });

        holder.itemView.setOnLongClickListener(v -> {

            if (listener != null) {
                listener.onRabbitTap(
                        rabbit,
                        holder.itemView
                );
            }

            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rabbitList == null
                ? 0
                : rabbitList.size();
    }

    static class RabbitViewHolder
            extends RecyclerView.ViewHolder {

        ImageView imgRabbit;
        TextView txtRabbitName;
        TextView txtRabbitBreedAge;
        View statusDot;
        ImageView imgQrBadge;

        RabbitViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            imgRabbit =
                    itemView.findViewById(
                            R.id.imgRabbit
                    );

            txtRabbitName =
                    itemView.findViewById(
                            R.id.txtRabbitName
                    );

            txtRabbitBreedAge =
                    itemView.findViewById(
                            R.id.txtRabbitBreedAge
                    );

            statusDot =
                    itemView.findViewById(
                            R.id.statusDot
                    );

            imgQrBadge =
                    itemView.findViewById(
                            R.id.imgQrBadge
                    );
        }
    }
}