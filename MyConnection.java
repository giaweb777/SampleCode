package com.miraicall.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.util.Log;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MyConnection extends Connection implements MyConnectionListener{
    Context c;

    MyConnection(Context context){
        setConnectionProperties(PROPERTY_SELF_MANAGED);
        setAudioModeIsVoip(true);
        // check if speaker  should be enabled and route audio on speaker if true
        if(CallContext.getInstance().isSpeakerenabled()){
            setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        }
        else{
            // get default bluetoothAdapter
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            //check if bluetooth exist and is connected and is an headset if true route audio to bluetooth
            //else route to default output
            if (bluetoothAdapter != null && BluetoothProfile.STATE_CONNECTED == bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)){
                setRoute(CallAudioState.ROUTE_BLUETOOTH);
            }
            else{
                setRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
            }
        }
        //set hold cappability
        setConnectionCapabilities(Connection.CAPABILITY_HOLD|CAPABILITY_SUPPORT_HOLD);
        //keep reference of the context for later use
        c = context;
    }

    //start the call activity if new incoming call
    @Override
    public void onShowIncomingCallUi() {
        super.onShowIncomingCallUi();

        Intent activityintent = new Intent(c,CallLockActivity.class);
        activityintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(activityintent);

    }


    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        super.onCallAudioStateChanged(state);
    }

    //tell the call service to put call on hold
    @Override
    public void onHold() {
        Intent startIntent = new Intent(c, CallService.class);
        startIntent.setAction("hold");
        c.startService(startIntent);
    }

    //tell the call service to unhold the call
    @Override
    public void onUnhold() {
        Intent startIntent = new Intent(c, CallService.class);
        startIntent.setAction("unhold");
        c.startService(startIntent);
    }

    @Override
    public void onAnswer() {
        Log.i("myConnection","onAnswer");
        super.onAnswer();
    }

    @Override
    public void onReject() {
        Log.i("myConnection","onReject");
        super.onReject();
    }

    @Override
    public void onDisconnect() {
        Log.i("myConnection","onDIsconnect");
        super.onDisconnect();
    }

    @Override
    public void active() {
        this.setActive();
    }

    @Override
    public void holded() {
        setOnHold();
    }

    @Override
    public void unholded() {
        setActive();

    }

    @Override
    public void setRoute(int route) {
        this.setAudioRoute(route);
    }
    /*
       call is closed disconnect the call
       and set the connection listner to null
       destroy the connection object
     */

    @Override
    public void close(int disconnectCause) {
        Log.i("myConnection","close");

        this.setDisconnected(new DisconnectCause(disconnectCause));
        CallContext.getInstance().setMcl(null);
        c= null;
        this.destroy();

    }




}

