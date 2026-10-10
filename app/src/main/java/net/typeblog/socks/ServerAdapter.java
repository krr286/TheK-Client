package net.typeblog.socks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.typeblog.socks.util.Profile;

import java.util.List;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.VH> {

    public interface Listener {
        void onSelect(Profile p);
        void onLongClick(Profile p);
    }

    private final List<Profile> mData;
    private final Listener mListener;
    private int mSelected = -1;

    public ServerAdapter(List<Profile> data, Listener l) {
        mData = data;
        mListener = l;
    }

    public void setSelectedIndex(int i) {
        mSelected = i;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_server, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Profile p = mData.get(pos);
        h.name.setText(p.getName());
        h.addr.setText(p.getServer() + ":" + p.getPort());
        h.flag.setText(flagFor(p.getServer()));
        h.check.setVisibility(pos == mSelected ? View.VISIBLE : View.INVISIBLE);
        if (h.ping != null) h.ping.setText("");
        if (h.load != null) h.load.setText("");
        if (h.action != null) h.action.setVisibility(View.GONE);

        h.itemView.setOnClickListener(v -> mListener.onSelect(p));
        h.itemView.setOnLongClickListener(v -> {
            mListener.onLongClick(p);
            return true;
        });
    }

    @Override
    public int getItemCount() { return mData.size(); }

    private static String flagFor(String ip) {
        if (ip == null) return "\uD83C\uDF10";
        String first = ip.contains(".") ? ip.substring(0, ip.indexOf('.')) : ip;
        int a;
        try { a = Integer.parseInt(first); } catch (Exception e) { return "\uD83C\uDF10"; }

        if (a == 138 || a == 41 || a == 156 || a == 197) return "\uD83C\uDDEA\uD83C\uDDEC";
        if (a >= 80 && a <= 95)  return "\uD83C\uDDE9\uD83C\uDDEA";
        if (a >= 176 && a <= 185) return "\uD83C\uDDF7\uD83C\uDDFA";
        if (a >= 5 && a <= 37) return "\uD83C\uDDEE\uD83C\uDDF9";
        if (a >= 51 && a <= 54) return "\uD83C\uDDEB\uD83C\uDDF7";
        if (a >= 100 && a <= 126) return "\uD83C\uDDFA\uD83C\uDDF8";
        return "\uD83C\uDF10";
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, addr, check, flag, ping, load, action;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.srv_name);
            addr = v.findViewById(R.id.srv_addr);
            check = v.findViewById(R.id.srv_check);
            flag = v.findViewById(R.id.srv_flag);
            ping = v.findViewById(R.id.srv_ping);
            load = v.findViewById(R.id.srv_load);
            action = v.findViewById(R.id.srv_action);
        }
    }
}
