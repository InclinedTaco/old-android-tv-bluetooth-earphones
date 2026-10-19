package local.tv.bluetoothhelper;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.bluetooth.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.os.*;
import android.util.Log;
import android.widget.*;
import java.util.*;
// Legacy permissions are normal permissions; Android 12+ operations are gated
// by initializeBluetooth(). Lint cannot follow that gate into receiver callbacks.
@SuppressLint("MissingPermission")
public class MainActivity extends Activity {
    private BluetoothAdapter adapter;
    private BluetoothProfile audio;
    private TextView status;
    private LinearLayout devices;
    private Button scanButton;
    private final Set<String> seen = new HashSet<>();
    private final Handler handler = new Handler();
    private String selectedAddress;
    private boolean registered, destroyed;
    private void note(String s) { Log.i("TvHeadphones", s); status.setText(s); }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(i.getAction())) add(d);
            else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(i.getAction()) && d != null) {
                if (d.getBondState() == BluetoothDevice.BOND_BONDED) {
                    note("Paired with " + name(d));
                    if (d.getAddress().equals(selectedAddress)) connect(d);
                } else if(d.getBondState() == BluetoothDevice.BOND_NONE) note("Pairing did not complete. Put the headphones in pairing mode and try again.");
            } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(i.getAction())) {
                int state=i.getIntExtra(BluetoothProfile.EXTRA_STATE,-1);
                note(state==2 ? "Connected for audio: " + name(d) : state==1 ? "Connecting audio…" : "Audio disconnected");
                if(state==2) { adapter.cancelDiscovery(); showPaired(); }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(i.getAction())) {
                scanButton.setText("Scan for headphones");
                Log.i("TvHeadphones", "Discovery finished; devices=" + seen.size());
            }
        }
    };
    private String name(BluetoothDevice d) { return d==null ? "Headphones" : d.getName()==null ? "Unnamed Bluetooth device" : d.getName(); }
    private boolean connected(BluetoothDevice d) { return audio!=null && audio.getConnectionState(d)==BluetoothProfile.STATE_CONNECTED; }
    private void add(BluetoothDevice d) {
        if(d==null || !seen.add(d.getAddress())) return;
        Log.i("TvHeadphones", "Found: " + name(d) + " bond=" + d.getBondState());
        Button b=button(name(d) + (connected(d) ? "   •   Connected" : d.getBondState()==12 ? "   •   Paired — connect" : "   •   Pair"));
        devices.addView(b);
        b.setOnClickListener(v -> { selectedAddress=d.getAddress(); adapter.cancelDiscovery(); if(d.getBondState()==12) connect(d); else { boolean ok=d.createBond(); note(ok ? "Pairing with " + name(d) + "… Select Pair in the TV confirmation." : "Could not start pairing. Try scanning again."); } });
    }
    private void showPaired() { devices.removeAllViews(); seen.clear(); for(BluetoothDevice d:adapter.getBondedDevices()) add(d); }
    private void connect(BluetoothDevice d) {
        if(audio==null) { note("Audio service is starting. Select the headphones again."); return; }
        if(connected(d)) { note("Already connected for audio: " + name(d)); return; }
        try {
            boolean ok=(Boolean)audio.getClass().getMethod("connect", BluetoothDevice.class).invoke(audio,d);
            note(ok ? "Connecting audio to " + name(d) + "…" : "Audio connection was declined. Disconnect the headphones from your phone and retry.");
        } catch(Exception e) { note("This firmware could not start the audio connection."); Log.e("TvHeadphones","connect",e); }
    }
    private GradientDrawable bg(int color, int stroke) { GradientDrawable g=new GradientDrawable(); g.setColor(color);g.setCornerRadius(14); if(stroke!=0)g.setStroke(2,stroke);return g; }
    private Button button(String title) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(18);b.setTextColor(Color.WHITE);b.setPadding(22,8,22,8);b.setBackground(bg(0xff17212e,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,58);p.setMargins(0,6,0,6);b.setLayoutParams(p);
        b.setOnFocusChangeListener((v,f)->b.setBackground(bg(f?0xff294760:0xff17212e,f?0xff8ecfff:0)));return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(44,26,44,20);root.setBackgroundColor(0xff090e15);
        TextView title=new TextView(this);title.setText("Bluetooth Audio");title.setTextSize(30);title.setTextColor(Color.WHITE);root.addView(title);
        status=new TextView(this);status.setTextSize(16);status.setTextColor(0xffadc0d3);status.setPadding(0,8,0,14);root.addView(status);
        LinearLayout actions=new LinearLayout(this);scanButton=button("Scan for headphones");Button test=button("Play a quiet test tone");
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,54,1);half.setMargins(0,0,12,10);actions.addView(scanButton,half);actions.addView(test,new LinearLayout.LayoutParams(0,54,1));root.addView(actions);
        ScrollView scroll=new ScrollView(this);devices=new LinearLayout(this);devices.setOrientation(LinearLayout.VERTICAL);scroll.addView(devices);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=new TextView(this);hint.setText("Put headphones in pairing mode, then select them here. Standard Bluetooth audio; compatibility varies by headset.");hint.setTextSize(13);hint.setTextColor(0xff8495a7);root.addView(hint);setContentView(root);
        scanButton.setOnClickListener(v->scan());
        test.setOnClickListener(v->{if(audio==null||audio.getConnectedDevices().isEmpty()){note("Connect headphones before playing the test tone.");return;} ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_MUSIC,15);tone.startTone(ToneGenerator.TONE_PROP_BEEP,350);handler.postDelayed(tone::release,600);});
        scanButton.requestFocus();note("Checking Bluetooth audio…");
        initializeBluetooth();
    }
    private boolean modernPermissions() {
        return Build.VERSION.SDK_INT < 31 || (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED);
    }
    private void initializeBluetooth() {
        if(!modernPermissions()) { requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_SCAN},2); return; }
        adapter=BluetoothAdapter.getDefaultAdapter();
        if(adapter==null || !adapter.isEnabled()) { note("Turn Bluetooth on in TV Settings first, then reopen this app."); return; }
        if(registered)return;
        IntentFilter f=new IntentFilter(BluetoothDevice.ACTION_FOUND);f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);registerReceiver(receiver,f);registered=true;
        adapter.getProfileProxy(this,new BluetoothProfile.ServiceListener(){public void onServiceConnected(int p,BluetoothProfile proxy){if(destroyed){adapter.closeProfileProxy(p,proxy);return;}audio=proxy;showPaired();note(proxy.getConnectedDevices().isEmpty()?"Select paired headphones or scan for a new pair.":"Connected for audio: "+name(proxy.getConnectedDevices().get(0)));} public void onServiceDisconnected(int p){audio=null;}},BluetoothProfile.A2DP);
    }
    private void scan(){
        if(!modernPermissions() || !registered){initializeBluetooth();return;}
        if(Build.VERSION.SDK_INT<31 && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},1);return;}
        showPaired();adapter.cancelDiscovery();boolean ok=adapter.startDiscovery();scanButton.setText(ok?"Scanning…":"Scan again");note(ok?"Searching for nearby devices. Select your headphones when they appear.":"Scan could not start. Try again.");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);boolean allowed=grants.length>0;for(int grant:grants)allowed&=grant==PackageManager.PERMISSION_GRANTED;if(!allowed){note("Bluetooth discovery needs the requested permission. Select Scan to retry.");return;}if(request==2)initializeBluetooth();else scan();}
    @Override public void onDestroy(){destroyed=true;if(registered)unregisterReceiver(receiver);if(adapter!=null&&modernPermissions()){adapter.cancelDiscovery();if(audio!=null)adapter.closeProfileProxy(BluetoothProfile.A2DP,audio);}super.onDestroy();}
}

