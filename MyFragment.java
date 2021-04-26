package com.miraicall.app;


import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.HashMap;
/*
    Default MyFragment and MyAsyncTaskResponseInterface implementation all other fragment extends this class

*/
public class MyFragment extends Fragment implements MyFragmentInterface,MyAsyncTaskResponseInterface {
    MainActivity parent;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        parent = (MainActivity)this.getActivity();

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        parent.setFragment(this);
        return super.onCreateView(inflater, container, savedInstanceState);
    }



    @Override
    public void onDestroyView (){
        super.onDestroyView();
        parent.setFragment(null);
    }
    /*
        Override this method if Fragment use Async task
     */
    @Override
    public void result(String msg) {

    }

    /*
        Override this method if Fragment needs to reset his views
        parameters :
        sender : Service asking reset ex: Background
        Type : type of reset if only a part of  views need to be reset
        name :  name of the data that need to be changed
        userinfo :  new data
     */
    @Override
    public void reset(String sender, String type, String name, HashMap<String, Object> userinfo) {

    }

    /*
        Default Error Handling, tells Fragment Activity to display an Error Dialog box
     */
    @Override
    public void error(String msg) {
        DialogInterface.OnClickListener pbuttonaction = (DialogInterface dialog, int id) ->{
            // User clicked OK button
            dialog.dismiss();

        };
        parent.runOnUiThread(()-> parent.alert(getString(R.string.error),msg,"ok",pbuttonaction,null,null,null,null,null,true));
    }

    /*
        Return parent activity if needed
     */
    @Override
    public Context getContext() {

        return parent;
    }

    /*
    Return Fragment if needed
     */
    @Override
    public MyFragment getFragment() {

        return this;
    }
}
