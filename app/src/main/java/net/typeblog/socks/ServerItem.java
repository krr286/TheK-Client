package net.typeblog.socks;

public class ServerItem {
    public static final int TYPE_SOCKS = 0;
    public static final int TYPE_MTPROTO = 1;

    public int type;
    public String name;
    public String host;
    public int port;
    public String user = "";
    public String pass = "";
    public String mtprotoLink = "";
    public String expiresAt = "";
    public int loadCur = 0;
    public int loadMax = 10;
    public int pingMs = -1; // -1 = не измерен, -2 = ошибка
    public boolean selected = false;

    public ServerItem(int type) { this.type = type; }
}
