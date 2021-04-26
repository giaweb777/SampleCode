package com.miraicall.app;

import org.webrtc.VideoTrack;

//singleton representing the call context
public class CallContext {

    private static CallContext instance;
    //connection listener used to communicate with the connection object
    private MyConnectionListener mcl = null;
    //Call Activity listener used to communicate with the Call activity
    private CallLockActivityListener clal = null;
    //call parameters
    private boolean isvideo = false;
    private boolean myvideo = false;
    private boolean isinitiator = false;
    private String calledid = "";
    private  boolean volumemuted = false;
    private  boolean micmuted = false;
    private  boolean speakerenabled = false;
    private  boolean onHold = false;
    private  int retry = 0;

    static CallStatusListener csl = null;
    //singleton getInstance
    public static CallContext getInstance(){
        if(instance == null){
            synchronized(CallContext.class)
            {
                if (instance == null)
                {
                    instance = new CallContext();
                }
            }
        }
        return  instance;
    }

    //check if instance still needed
    private void needRelease(){
        if(clal == null && csl == null && mcl == null){
            instance = null;
        }
    }

    //set MyConnectionListener
    public void setMcl(MyConnectionListener mcl) {

        this.mcl = mcl;
    }

    //if connection is closed tell the connection to close and delete the listener
    public void conclose(int cause){

        if(mcl != null){
            mcl.close(cause);
            mcl = null;
            needRelease();
        }
    }

    // update connection status to active
    public void conactive(){
        if(mcl != null){
            mcl.active();
        }
    }

    // update connection status to hold
    public  void conhold(){
        if(mcl != null){
            mcl.holded();
        }
    }

    //change audio route ( speaker or other)
    public  void setRoute(int route){
        if(mcl != null){
            mcl.setRoute(route);
        }
    }
    // update connection status to unhold
    public  void conunhold(){
        if(mcl != null){
            mcl.unholded();
        }
    }



    // update call activity listener
    // check if the context needs to be destroy (clal can be null)
    public void setClal(CallLockActivityListener clal){

        this.clal = clal;
        needRelease();
    }

    //tell Call Activity to update local video stream and init textureviewrender if needed
    public void updateLocalSink(TextureViewRenderer svr, boolean needinit){
        if(clal != null){
            clal.updateLocalSink(svr, needinit);
        }

    }

    //tell Call Activity to update remote video stream and init textureviewrender if needed
    public boolean updateRemoteSink(String fid, TextureViewRenderer svr, boolean needinit){
        if(clal != null){
            return clal.updateRemoteSink(fid,svr,needinit);
        }
        return false;
    }

    //tell Call Activity to remove local video stream
    public void removeLocalSink(TextureViewRenderer svr){
        if(clal != null){
            clal.removeLocalSink(svr);
        }
    }

    //tell Call Activity to remove remote video stream
    public void removeRemoteSink(String fid, TextureViewRenderer svr){
        if(clal != null){
            clal.removeRemoteSink(fid,svr);
        }
    }

    // update CallStatusListener
    // check if the context needs to be destroy (callStatusListeners can be null)
    public void setCallStatusListener(CallStatusListener callStatusListeners){
        csl = callStatusListeners;
        needRelease();
    }

    //tell to attach local videotrack to textureview
    public TextureViewRenderer addLocalVideoTrack(CallService cs, VideoTrack vt){
        if(csl != null){
            csl.onLocalVideoTrackAdd(cs,vt);
        }
        return null;
    }
    //tell to attach remote videotrack to textureview
    public TextureViewRenderer addRemoteVideoTrack(CallService cs,VideoTrack vt,String fid){
        if(csl != null){
            return csl.onVideoTrackAdd(cs,vt,fid);
        }
        return null;
    }

    //tell the activity that call status changed
    //force is used to foce the view update.
    public  void StatusChange(boolean force){

        if(csl != null){
            csl.OnStatusChange(force);
        }
    }

    public boolean isIsvideo() {
        return isvideo;
    }

    public void setIsvideo(boolean isvideo) {
        this.isvideo = isvideo;
    }

    public boolean isMyvideo() {
        return myvideo;
    }

    public void setMyvideo(boolean myvideo) {
        this.myvideo = myvideo;
    }

    public boolean isIsinitiator() {
        return isinitiator;
    }

    public void setIsinitiator(boolean isinitiator) {
        this.isinitiator = isinitiator;
    }

    public String getCalledid() {
        return calledid;
    }

    public void setCalledid(String calledid) {
        if(calledid != null){
            this.calledid = calledid;
        }

    }



    public boolean isVolumemuted() {
        return volumemuted;
    }

    public void setVolumemuted(boolean volumemuted) {
        this.volumemuted = volumemuted;
    }

    public boolean isMicmuted() {
        return micmuted;
    }

    public void setMicmuted(boolean micmuted) {
        this.micmuted = micmuted;
    }

    public boolean isSpeakerenabled() {
        return speakerenabled;
    }

    public void setSpeakerenabled(boolean speakerenabled) {
        this.speakerenabled = speakerenabled;
    }

    public boolean isOnHold() {
        return onHold;
    }

    public void setOnHold(boolean onHold) {
        this.onHold = onHold;
    }

    public int getRetry() {
        return retry;
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }
}
