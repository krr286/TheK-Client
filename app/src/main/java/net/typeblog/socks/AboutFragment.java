package net.typeblog.socks;

import android.app.Fragment;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup c, Bundle s) {
        ScrollView sv = new ScrollView(getActivity());
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        // === Заголовок THEK ===
        TextView title = new TextView(getActivity());
        title.setText("THEK");
        title.setTextSize(42);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        title.setShadowLayer(12, 0, 3, 0x707C5CFF);
        title.setPadding(0, 20, 0, 6);
        root.addView(title);

        TextView sub = new TextView(getActivity());
        sub.setText("P R O X Y   C L I E N T");
        sub.setTextSize(10);
        sub.setTextColor(0xFFA0A8BF);
        sub.setGravity(Gravity.CENTER);
        sub.setLetterSpacing(0.25f);
        sub.setPadding(0, 0, 0, 40);
        root.addView(sub);

        // === Карточка с текстом о приложении ===
        LinearLayout card = new LinearLayout(getActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundResource(R.drawable.bg_info_card);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardLp);

        String aboutText = RemoteAssets.getText(getActivity(), "text_about",
            "THEK VPN — защита и скорость.\n\nПриложение для безопасного и быстрого доступа к сети через SOCKS5 и Telegram MTProto.\n\n© THEK, 2026");

        TextView aboutView = new TextView(getActivity());
        aboutView.setText(aboutText);
        aboutView.setTextSize(16);
        aboutView.setTextColor(0xFFE0E4F0);
        aboutView.setLineSpacing(8, 1f);
        aboutView.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(aboutView);

        root.addView(card);

        // === Карточка с версией ===
        LinearLayout versionCard = new LinearLayout(getActivity());
        versionCard.setOrientation(LinearLayout.HORIZONTAL);
        versionCard.setPadding(24, 20, 24, 20);
        versionCard.setGravity(Gravity.CENTER_VERTICAL);
        versionCard.setBackgroundResource(R.drawable.bg_info_card);

        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        vLp.setMargins(0, 14, 0, 0);
        versionCard.setLayoutParams(vLp);

        TextView vLabel = new TextView(getActivity());
        vLabel.setText("Версия");
        vLabel.setTextSize(14);
        vLabel.setTextColor(0xFFA0A8BF);
        vLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        versionCard.addView(vLabel);

        String versionName = "1.0";
        try {
            versionName = getActivity().getPackageManager()
                .getPackageInfo(getActivity().getPackageName(), 0).versionName;
        } catch (Exception ignored) {}

        TextView vValue = new TextView(getActivity());
        vValue.setText(versionName);
        vValue.setTextSize(14);
        vValue.setTextColor(0xFF7C5CFF);
        vValue.setTypeface(null, android.graphics.Typeface.BOLD);
        versionCard.addView(vValue);

        root.addView(versionCard);

        sv.addView(root);
        return sv;
    }
}
