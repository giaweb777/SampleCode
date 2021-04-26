package com.miraicall.app;


import android.content.Context;

import java.util.HashMap;


interface MyFragmentInterface {

    void reset(String sender, String type, String name, HashMap<String,Object> userinfo);
    Context getContext();
    MyFragment getFragment();

}
