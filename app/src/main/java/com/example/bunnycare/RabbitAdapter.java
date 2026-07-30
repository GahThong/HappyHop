package com.example.bunnycare;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RabbitAdapter extends RecyclerView.Adapter<RabbitAdapter.RabbitViewHolder> {

    private Context context;
    private List<Rabbit> rabbitList;

    public RabbitAdapter(Context context, List<Rabbit> rabbitList) {
        this.context = context;
        this.rabbitList = rabbitList;
    }

    @NonNull
    @Override
    public RabbitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_rabbit_card, parent, false);
        return new RabbitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RabbitViewHolder holder, int position) {

        Rabbit rabbit = rabbitList.get(position);

        holder.txtRabbitName.setText(rabbit.getRabbitName());

        // TODO: Load image using Glide/Picasso if imageUrl exists
        // Glide.with(context).load(rabbit.getImageUrl()).into(holder.imgRabbit);

        holder.imgQrBadge.setVisibility(
                rabbit.isHasQr() ? View.VISIBLE : View.GONE
        );
    }

    @Override
    public int getItemCount() {
        return rabbitList.size();
    }

    static class RabbitViewHolder extends RecyclerView.ViewHolder {

        ImageView imgRabbit;
        ImageView imgQrBadge;
        TextView txtRabbitName;
        ImageButton btnEditRabbit;
        ImageButton btnDeleteRabbit;

        public RabbitViewHolder(@NonNull View itemView) {
            super(itemView);

            imgRabbit = itemView.findViewById(R.id.imgRabbit);
            imgQrBadge = itemView.findViewById(R.id.imgQrBadge);
            txtRabbitName = itemView.findViewById(R.id.txtRabbitName);
            btnEditRabbit = itemView.findViewById(R.id.btnEditRabbit);
            btnDeleteRabbit = itemView.findViewById(R.id.btnDeleteRabbit);
        }
    }
}