package net.typeblog.socks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ServerUnifiedAdapter extends RecyclerView.Adapter<ServerUnifiedAdapter.VH> {

    public interface Listener {
        void onSelect(ServerItem item);
        void onLongClick(ServerItem item);
        void onMtprotoClick(ServerItem item);
    }

    private final List<ServerItem> mData;
    private final Listener mListener;

    public ServerUnifiedAdapter(List<ServerItem> data, Listener l) {
        mData = data;
        mListener = l;
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
        ServerItem it = mData.get(pos);
        boolean isMt = it.type == ServerItem.TYPE_MTPROTO;

        h.name.setText(it.name);
        h.addr.setText(it.host + ":" + it.port);
        h.flag.setText(flagFor(it));
        h.check.setVisibility(!isMt && it.selected ? View.VISIBLE : View.INVISIBLE);

        if (h.load != null) {
            if (it.loadMax > 0) {
                double pct = (double) it.loadCur / it.loadMax;
                String ind = pct < 0.5 ? "\uD83D\uDFE2" : pct < 0.85 ? "\uD83D\uDFE1" : "\uD83D\uDD34";
                h.load.setText(ind + " " + it.loadCur + "/" + it.loadMax);
            } else {
                h.load.setText("");
            }
        }

        if (h.ping != null) {
            if (it.pingMs == -1) {
                h.ping.setText("\u00B7\u00B7\u00B7");
                h.ping.setTextColor(0x99FFFFFF);
            } else if (it.pingMs < 0) {
                h.ping.setText("\u043D\u0435\u0442 \u0441\u0432\u044F\u0437\u0438");
                h.ping.setTextColor(0xFFFF3F3F);
            } else {
                h.ping.setText(it.pingMs + " \u043C\u0441");
                if (it.pingMs < 150) h.ping.setTextColor(0xFF3FFF7A);
                else if (it.pingMs < 400) h.ping.setTextColor(0xFFFFCC33);
                else h.ping.setTextColor(0xFFFF3F3F);
            }
        }

        if (isMt) {
            if (h.action != null) {
                h.action.setVisibility(View.VISIBLE);
                h.action.setOnClickListener(v -> mListener.onMtprotoClick(it));
            }
            h.itemView.setOnClickListener(v -> mListener.onMtprotoClick(it));
            h.itemView.setOnLongClickListener(null);
        } else {
            if (h.action != null) h.action.setVisibility(View.GONE);
            h.itemView.setOnClickListener(v -> mListener.onSelect(it));
            h.itemView.setOnLongClickListener(v -> {
                mListener.onLongClick(it);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() { return mData.size(); }

    private static String flagFor(ServerItem it) {
        if (it.type == ServerItem.TYPE_MTPROTO) return "\u2708\uFE0F";
        String ip = it.host;
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
