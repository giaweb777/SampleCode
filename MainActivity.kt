package com.miraicall.app


import android.Manifest
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.android.ump.*
import com.miraicall.app.*
import com.miraicall.app.MyApp.Companion.getCircularBitmap
import com.miraicall.app.database.AppDatabase
import com.miraicall.app.database.Conversation
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.Executors


class MainActivity : FragmentActivity(), SyncListener, MyAsyncTaskResponseInterface, AccountListener {
    private var newFragment: MyFragment? = null
    private var dialog: Dialog? = null
    private var fragment: String? = ""
    private var backstack = ""
    var db1: AppDatabase? = null
    var islogin = false

    override fun onTrimMemory(level: Int) {
        Log.i("Memory", "trim $level")

    }

    //function to show a progress bar in a dialog box
    //called by child fragment
    fun showProgressBar() {
        dialog?.dismiss()
        dialog = Dialog(this)
        val builder = MyCustomDialog.Builder(this)
        dialog = builder.createPB()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.show()
    }

    //function use to dismiss curent dialog box
    //called by child fragment
    fun dissmissdialog() {
        dialog?.dismiss()
    }

    private var consentInformation: ConsentInformation? = null

    private var consentForm: ConsentForm? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //log.i("memory","available = "+memoryInfo.availMem+" treshold = "+memoryInfo.threshold+" total mem = "+memoryInfo.totalMem+" islowmem = "+memoryInfo.lowMemory);
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("fragment")) {
                fragment = savedInstanceState.getString("fragment")
            }
        }
        val am = AccountManager.get(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //create phone account handle used to create call
            val pah = PhoneAccountHandle(ComponentName(applicationContext, MyConnectionService::class.java), packageName)
            val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
            val pa = PhoneAccount.builder(pah, packageName).setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED).build()
            tm.registerPhoneAccount(pa)

        }
        if (am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE).size > 0) {
            //start signaling service if at least 1 account exist
            val pushIntent = Intent(this, SignalService::class.java)
            startService(pushIntent)

        }
        setContentView(R.layout.mainlayout)
        //used for google  cookies consent popup form
        val debugSettings = ConsentDebugSettings.Builder(this).addTestDeviceHashedId("B3EEABB8EE11C2BE770B684D95219ECB").build()
        val params = ConsentRequestParameters.Builder()
                .setConsentDebugSettings(debugSettings).build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation?.requestConsentInfoUpdate(
                this,
                params,
                { // The consent information state was updated.
                    // You are now ready to check if a form is available.
                    if (consentInformation!!.isConsentFormAvailable) {
                        loadForm()
                    } else {
                        if (consentInformation!!.consentStatus == ConsentInformation.ConsentStatus.OBTAINED || consentInformation!!.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED) {
                            //if we get consent we start mobileads
                            MobileAds.initialize(this@MainActivity) { }
                        }
                    }
                },
                { Log.i("consent", "form error") })


    }

    fun loadForm() {
        UserMessagingPlatform.loadConsentForm(
                this,
                { consentForm ->
                    this@MainActivity.consentForm = consentForm
                    if (consentInformation!!.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                        consentForm.show(
                                this@MainActivity
                        ) { // Handle dismissal by reloading form.
                            loadForm()
                        }
                    } else if (consentInformation!!.consentStatus == ConsentInformation.ConsentStatus.OBTAINED || consentInformation!!.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED) {
                        MobileAds.initialize(this@MainActivity) { }
                    }
                }
        ) {
            /// Handle Error.
        }
    }

    override fun onStart() {
        super.onStart()

        val p = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 23) {
            //check permission and add permission request if false
            if (this.applicationContext.checkSelfPermission(
                            Manifest.permission.WRITE_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) {
                p.add(Manifest.permission.WRITE_CONTACTS)
                p.add(Manifest.permission.READ_CONTACTS)
            }
            if (this.applicationContext.checkSelfPermission(
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                p.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (this.applicationContext.checkSelfPermission(
                            Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                p.add(Manifest.permission.RECORD_AUDIO)
            }
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

                p.add(Manifest.permission.READ_PHONE_STATE)
            }
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {

                p.add(Manifest.permission.CALL_PHONE)
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                p.add(Manifest.permission.CAMERA)
            }
            if (p.size > 0) {
                val perm = arrayOfNulls<String>(p.size)
                p.toArray(perm)
                requestPermissions(
                        perm, 23)
            }

        }

        try {
            // check if GooglePlayService is installed
            ProviderInstaller.installIfNeeded(applicationContext)
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //add notificationChannel group and notificationchannel
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannelGroup(NotificationChannelGroup("$packageName.callgroup", getString(R.string.call)))
            var chan = NotificationChannel("$packageName.nocall", getString(R.string.status), NotificationManager.IMPORTANCE_LOW)
            chan.enableLights(true)
            chan.lightColor = Color.GREEN
            chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            chan.group = "$packageName.callgroup"
            var uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.nosound)
            chan.setSound(uri, null)
            manager.createNotificationChannel(chan)
            chan = NotificationChannel("$packageName.call", getString(R.string.call), NotificationManager.IMPORTANCE_HIGH)
            chan.enableLights(true)
            chan.lightColor = Color.GREEN
            chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.nosound)
            chan.setSound(uri, null)
            chan.group = "$packageName.callgroup"
            manager.createNotificationChannel(chan)
            chan = NotificationChannel("$packageName.msg", getString(R.string.message), NotificationManager.IMPORTANCE_DEFAULT)
            chan.lightColor = Color.GREEN
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannelGroup(NotificationChannelGroup("$packageName.messagegroup", getString(R.string.message)))
            manager.createNotificationChannel(chan)
            chan = NotificationChannel("$packageName.request", getString(R.string.request), NotificationManager.IMPORTANCE_HIGH)
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannelGroup(NotificationChannelGroup("$packageName.requestgroup", getString(R.string.request)))
            manager.createNotificationChannel(chan)
        }
    }

    /*override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    }*/

    public override fun onPostResume() {
        super.onPostResume()
        val am = AccountManager.get(this)
        val pushIntent = Intent(this, SignalService::class.java)
        startService(pushIntent)
        if (am.getAccountsByType(AuthenticatorService.Companion.ACCOUNT_TYPE).size > 0) {
            if (newFragment == null) {
                // if fragment is null we start and 1 account exist we start the main manu
                val ac = am.getAccountsByType(AuthenticatorService.Companion.ACCOUNT_TYPE)[MyApp.selectedaccount]
                val params = Bundle()
                params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                ContentResolver.requestSync(ac, ContactsContract.AUTHORITY, params)

                newFragment = mainmenupager()
            }
            if (this.supportFragmentManager.findFragmentByTag(newFragment!!.tag) == null) {
                // if fragment exist in the fragment manager we start this fragment and add it to backstack if needed
                val transaction = this.supportFragmentManager.beginTransaction()
                transaction.replace(R.id.mainlayout, newFragment!!, newFragment!!.tag)
                if (backstack != "") {
                    transaction.addToBackStack(backstack)
                }
                transaction.commit()
                this.supportFragmentManager.executePendingTransactions()
            }


        } else {
            if (newFragment == null) {
                // no account and no fragment, start the login fragment
                newFragment = Login.newInstance()
                val transaction = this.supportFragmentManager.beginTransaction()
                transaction.replace(R.id.mainlayout, newFragment!!, newFragment!!.getTag())
                transaction.commit()
                this.supportFragmentManager.executePendingTransactions()
            }
            if (this.supportFragmentManager.findFragmentByTag(newFragment!!.tag) == null) {
                // start old fragment
                val transaction = this.supportFragmentManager.beginTransaction()
                transaction.replace(R.id.mainlayout, newFragment!!, newFragment!!.tag)
                transaction.commit()
                this.supportFragmentManager.executePendingTransactions()
            }
        }
        backstack = ""
    }


    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        islogin = savedInstanceState.getBoolean("islogin", false)
    }

    //use when start activity is called
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleIntent(intent)
    }

    // invoked when the activity may be temporarily destroyed, save the instance state here
    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("islogin", islogin)
        outState.putString("fragment", fragment)
        // call superclass to save any view hierarchy
        super.onSaveInstanceState(outState)
    }

    //add broadcast receiver for intent filter
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            handleIntent(intent)
        }
    }

    //used to get instance of database
    fun getdb(): AppDatabase {
        if (db1 == null || !db1!!.isOpen) {
            db1 = Room.databaseBuilder(this,
                    AppDatabase::class.java, "memeber")
                    .addMigrations(*MyApp.ALL_MIGRATIONS)
                    .enableMultiInstanceInvalidation()
                    .build()
        }
        return db1!!
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(mReceiver, IntentFilter("com.miraicall.update"))
        (application as MyApp).setAccountListener(this)
        val intent = intent
        if (intent != null) {
            if (Intent.ACTION_VIEW == intent.action) {

                val data = intent.data
                if (data != null) {
                    //Create programatically the view for password reset
                    val ps = data.pathSegments

                    val ub = Uri.Builder()
                    ub.scheme("https").authority(BuildConfig.BASE_FQDN)
                    ub.appendPath("mobile")
                    for (i in 1 until ps.size) {
                        ub.appendPath(ps[i])
                    }
                    val uri = ub.build()

                    val ll = LinearLayout(this)
                    ll.orientation = LinearLayout.VERTICAL
                    val et = EditText(this)
                    val density = resources.displayMetrics.density
                    val scaled = (5 * density).toInt()
                    et.hint = getString(R.string.password)
                    et.setPadding(scaled, scaled, scaled, scaled)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, scaled, 0, scaled)
                    et.layoutParams = lp
                    et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    val et2 = EditText(this)
                    et2.setPadding(scaled, scaled, scaled, scaled)
                    et2.hint = getString(R.string.confirm_password)
                    et2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    et2.layoutParams = lp
                    val tv = TextView(this)
                    tv.layoutParams = lp
                    tv.setPadding(scaled, scaled, scaled, scaled)
                    tv.text = getString(R.string.emptypass)
                    tv.setTextColor(Color.RED)
                    tv.visibility = View.VISIBLE
                    val tw: TextWatcher = object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            if ("" == et.text.toString()) {
                                et.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                                et2.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                                tv.text = getString(R.string.emptypass)
                                tv.visibility = View.VISIBLE
                            } else {
                                if (et.text.toString() == et2.text.toString()) {
                                    et.background = ResourcesCompat.getDrawable(resources, R.drawable.bordergreen, null)
                                    et2.background = ResourcesCompat.getDrawable(resources, R.drawable.bordergreen, null)
                                    tv.visibility = View.GONE
                                } else {
                                    et.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                                    et2.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                                    tv.text = getString(R.string.passmiss)
                                    tv.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                    et.addTextChangedListener(tw)
                    et2.addTextChangedListener(tw)
                    et.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                    et2.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                    ll.addView(et)
                    ll.addView(et2)
                    ll.addView(tv)
                    val pbuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, id: Int ->
                        if ("" == et.text.toString()) {
                            et.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                            et2.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                            tv.text = getString(R.string.emptypass)
                            tv.visibility = View.VISIBLE
                        } else {
                            if (et.text.toString() == et2.text.toString()) {
                                et.background = ResourcesCompat.getDrawable(resources, R.drawable.bordergreen, null)
                                et2.background = ResourcesCompat.getDrawable(resources, R.drawable.bordergreen, null)
                                tv.visibility = View.GONE
                                val mati: MyAsyncTaskResponseInterface = object : MyAsyncTaskResponseInterface {
                                    override fun result(msg: String) {
                                        dialog.dismiss()
                                        val cuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, id: Int -> dialog.dismiss() }
                                        runOnUiThread { alert(getString(R.string.changepass), getString(R.string.ucomplete), getString(R.string.ok), cuttonaction, null, null, null, null, null, true) }
                                    }

                                    override fun error(msg: String?) {
                                        dialog.dismiss()
                                        val cuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, id: Int -> dialog.dismiss() }
                                        runOnUiThread { alert(getString(R.string.changepass), msg, getString(R.string.ok), cuttonaction, null, null, null, null, null, true) }
                                    }
                                }
                                try {
                                    val url = URL(uri.toString())
                                    val post = URLEncoder.encode("password", "UTF-8") + "=" + URLEncoder.encode(et.text.toString(), "UTF-8")
                                    MyAsyncTask(mati, url, post, true, false).execute(this)
                                } catch (e: MalformedURLException) {
                                    e.printStackTrace()
                                } catch (e: UnsupportedEncodingException) {
                                    e.printStackTrace()
                                }
                            } else {
                                et.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                                et2.background = ResourcesCompat.getDrawable(resources, R.drawable.borderred, null)
                                tv.text = getString(R.string.passmiss)
                                tv.visibility = View.VISIBLE
                            }
                        }
                    }
                    val cuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, id: Int -> dialog.dismiss() }
                    runOnUiThread { alert(getString(R.string.changepass), null, getString(R.string.ok), pbuttonaction, null, null, getString(R.string.cancel), cuttonaction, ll, true) }
                }
            }
            if (intent.extras != null) {
                handleIntent(intent)
            }
        }
    }

    //function used for creating alert dialog

    fun alert(title: String?, message: String?, pbuttontitle: String?, pbuttonaction: DialogInterface.OnClickListener?, neutralbuttontitle: String?, neutralbuttonaction: DialogInterface.OnClickListener?, nbuttontitle: String?, nbuttonaction: DialogInterface.OnClickListener?, customview: View?, cancelable: Boolean?) {
        dialog?.dismiss()
        val builder = MyCustomDialog.Builder(this)

        builder.setTitle(title)
        if (message != null) {
            builder.setMessage(message)
        }
        if (pbuttontitle != null) {
            builder.setPositiveButton(pbuttontitle, pbuttonaction)
        }
        if (neutralbuttontitle != null) {
            builder.setNeutralButton(neutralbuttontitle, neutralbuttonaction)
        }
        if (nbuttontitle != null) {
            builder.setNegativeButton(nbuttontitle, nbuttonaction)
        }
        if (customview != null) {
            builder.setView(customview)
        }
        dialog = builder.create()
        dialog?.setCanceledOnTouchOutside(cancelable!!)
        dialog?.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val am = AccountManager.get(this)
        if (am.getAccountsByType(AuthenticatorService.Companion.ACCOUNT_TYPE).size > 0) {
            //inflate menu if account exist
            val inflater = menuInflater
            inflater.inflate(R.menu.mainmenu, menu)
            var found = false
            //add autostart to menu for Chinese Android rom
            for (intent in Constants.AUTO_START_INTENTS) {
                val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (list.size > 0) {
                    found = true
                }
            }
            if (!found) {
                menu.removeItem(R.id.autostart)
            }
        }
        return true
    }

    //menu handling
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.logout -> {
                removeAccount(MyApp.selectedaccount)
                false
            }
            R.id.groupcall -> {
                try {
                    val fids = ContactManager.getCallStatusfids(this)
                    if (fids.size > 0) {
                        val activityintent = Intent(this, CallLockActivity::class.java)
                        activityintent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(activityintent)
                    } else {
                        buildGroupCallList()
                    }
                } catch (e: OperationApplicationException) {
                    e.printStackTrace()
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                false
            }
            R.id.groupmsg -> {
                buildGroupMsgList()
                false
            }
            R.id.blocklist -> {
                buildBlockList()
                false
            }
            R.id.addaccount -> {
                newFragment = Login.Companion.newInstance()
                val transaction = this.supportFragmentManager.beginTransaction()
                transaction.addToBackStack("main")
                transaction.replace(R.id.mainlayout, newFragment!!, newFragment!!.tag)
                transaction.commit()
                this.supportFragmentManager.executePendingTransactions()
                false
            }
            R.id.dela -> {
                try {
                    val url = URL(getString(R.string.delaurl, BuildConfig.BASE_URL))
                    val passw = ContactManager.GetPassw(this)
                    var params = URLEncoder.encode("os", "UTF-8") + "=" + URLEncoder.encode("ANDROID", "UTF-8")
                    params += "&" + URLEncoder.encode("passw", "UTF-8") + "=" + URLEncoder.encode(passw, "UTF-8")
                    val acc = MyApp.selectedaccount
                    val myid = ContactManager.GetAccountsID(this, acc)
                    val matri: MyAsyncTaskResponseInterface = object : MyAsyncTaskResponseInterface {
                        override fun result(msg: String) {
                            if (myid == ContactManager.GetAccountsID(this@MainActivity, acc)) {
                                removeAccount(acc)
                            }
                        }

                        override fun error(msg: String?) {}
                    }
                    MyAsyncTask(matri, url, params, acc).execute(this)
                } catch (e: MalformedURLException) {
                    e.printStackTrace()
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }

                super.onOptionsItemSelected(item)
            }
            R.id.autostart -> {
                for (intent in Constants.AUTO_START_INTENTS) {
                    val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    if (list.size > 0) {
                        Log.i("start intent", intent.toString())
                        this.startActivity(intent)
                        break
                    }
                }
                super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    //create list for group message selection
    private fun buildGroupMsgList() {
        val cbids = ArrayList<String>()
        cbids.clear()
        val rv = RecyclerView(this)
        rv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100)
        rv.layoutManager = MyLayoutManager(this)
        rv.adapter = ContactSelectAdapter(ContactManager.GetContactList(this, MyApp.selectedaccount)!!, this, cbids)
        val pbuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, _: Int ->
            val ids = StringBuilder()
            var vir = ""
            for (i in cbids.indices) {
                ids.append(vir)
                ids.append(cbids[i])
                vir = ","
            }
            val newFragment: Fragment = Conv.Companion.newInstance(this, ids.toString())
            val transaction = this.supportFragmentManager.beginTransaction()
            transaction.addToBackStack("main")
            transaction.replace(R.id.mainlayout, newFragment, "dial")
            transaction.commit()
            this.supportFragmentManager.executePendingTransactions()
            dialog.dismiss()
        }
        val cuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, id: Int -> dialog.dismiss() }
        runOnUiThread { alert(getString(R.string.groupmsg), null, getString(R.string.ok), pbuttonaction, null, null, getString(R.string.cancel), cuttonaction, rv, true) }
    }

    //create list of blocked users
    private fun buildBlockList() {
        val cbids = ArrayList<String>()
        cbids.clear()
        val rv = RecyclerView(this)
        rv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100)
        rv.layoutManager = MyLayoutManager(this)
        rv.adapter = BlockedAdapter(this, cbids)
        val pbuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface?, id: Int ->
            for (i in cbids.indices) {
                val passw = ContactManager.GetPassw(this)
                try {
                    val url = URL(resources.getString(R.string.blockuserurl, BuildConfig.BASE_URL))
                    var params = URLEncoder.encode("os", "UTF-8") + "=" + URLEncoder.encode("ANDROID", "UTF-8")
                    params += "&" + URLEncoder.encode("passw", "UTF-8") + "=" + URLEncoder.encode(passw, "UTF-8")
                    params += "&" + URLEncoder.encode("fid", "UTF-8") + "=" + URLEncoder.encode(cbids[i], "UTF-8")
                    params += "&" + URLEncoder.encode("val", "UTF-8") + "=" + URLEncoder.encode("0", "UTF-8")
                    val es = Executors.newSingleThreadExecutor()
                    MyAsyncTask(this, url, params).executeOnExecutor(es, this)
                    es.shutdown()
                } catch (e: MalformedURLException) {
                    e.printStackTrace()
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            }
        }
        val cuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, id: Int -> dialog.dismiss() }
        runOnUiThread { alert(getString(R.string.blocklist), null, getString(R.string.unblock), pbuttonaction, null, null, getString(R.string.cancel), cuttonaction, rv, true) }
    }

    //create list for group call selection
    fun buildGroupCallList() {
        val cbids = ArrayList<String>()
        cbids.clear()
        val rv = RecyclerView(this)
        rv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100)
        rv.layoutManager = MyLayoutManager(this)
        rv.adapter = ContactSelectAdapter(ContactManager.GetOnlineContactList(this, MyApp.selectedaccount)!!, this, cbids)
        val pbuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, id: Int ->
            try {
                for (i in cbids.indices) {
                    ContactManager.setCallStatus(this, cbids[i], ContactManager.STATUS_CLOSED)

                }
                val startIntent = Intent(this, CallLockActivity::class.java)
                startIntent.putExtra("myid", ContactManager.GetAccountsID(this, MyApp.selectedaccount))
                startIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                this.startActivity(startIntent)
                dialog.dismiss()
            } catch (e: OperationApplicationException) {
                e.printStackTrace()
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
        val cuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, id: Int -> dialog.dismiss() }
        runOnUiThread { alert(getString(R.string.groupcall), null, getString(R.string.call), pbuttonaction, null, null, getString(R.string.cancel), cuttonaction, rv, true) }
    }

    public override fun onPause() {
        super.onPause()
        unregisterReceiver(mReceiver)
        (application as MyApp).setAccountListener(null)
    }

    public override fun onDestroy() {
        super.onDestroy()
        db1?.close()
        db1 = null
    }

    //handle back button press
    override fun onBackPressed() {
        if (newFragment is Create_acc) {
            newFragment = Login.newInstance()
            val transaction = this.supportFragmentManager.beginTransaction()
            transaction.replace(R.id.mainlayout, newFragment!!, newFragment!!.getTag())
            transaction.commit()
            this.supportFragmentManager.executePendingTransactions()
        } else if (supportFragmentManager.backStackEntryCount > 0) {
            val fname = "" + supportFragmentManager.getBackStackEntryAt(supportFragmentManager.backStackEntryCount - 1).name
            supportFragmentManager.popBackStack()
            if (fname == "update") {
                recreate()
            }
        } else {
            super.onBackPressed()
        }
    }


    fun setFragment(fr: MyFragmentInterface?) {
        if (fr != null) {
            fragment = fr.javaClass.name
            if (fr.javaClass == Login::class.java || fr.javaClass == Create_acc::class.java) {
                val mAdView: AdView = findViewById(R.id.banner)
                mAdView.destroy()
                mAdView.visibility = View.GONE
            } else {
                val mAdView: AdView = findViewById(R.id.banner)
                val adRequest = AdRequest.Builder()
                        .build()
                mAdView.visibility = View.VISIBLE
                mAdView.loadAd(adRequest)
            }
            newFragment = fr.fragment
        } else {
            newFragment = null
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.hasExtra("tab")) {
            val tab = intent.getIntExtra("tab", -1)
            if (tab == MainActivity.MSG_INTENT) {
                val conv = intent.getStringExtra("conv")
                val json = intent.getStringExtra("json")
                val acc = intent.getIntExtra("acc", -1)
                if (newFragment is Conv) {
                    runOnUiThread { newFragment!!.reset("msg", conv!!, json, null) }
                } else {
                    try {
                        if (json != null) {
                            val jo = JSONObject(json)
                            val sid = jo.getJSONObject("message").getString("sid")
                            if (ContactManager.GetAccountsID(this, acc) != sid) {
                                val activityintent = Intent(this, MainActivity::class.java)
                                activityintent.putExtra("tab", MainActivity.Companion.SHOW_INTENT)
                                activityintent.putExtra("conv", conv)
                                activityintent.action = "me.memeber.action.MAIN"
                                activityintent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                val pi = PendingIntent.getActivity(this,
                                        0, activityintent,
                                        PendingIntent.FLAG_UPDATE_CURRENT)
                                val mBuilder = NotificationCompat.Builder(this, "app.me request")
                                        .setSmallIcon(R.drawable.ic_message_white_24dp)
                                        .setColor(ContextCompat.getColor(this, R.color.blue))
                                        .setContentTitle(this.resources.getString(R.string.incommingmsg) + " " + jo.getJSONObject("message").getString("name"))
                                        .setContentText(String(Base64.decode(jo.getJSONObject("message").getString("value"), Base64.DEFAULT)))
                                        .setAutoCancel(true)
                                        .setContentIntent(pi)
                                if (ContactManager.ContactExist(this, acc, sid)) {
                                    val ba = ContactManager.GetContactPhoto(this, acc, sid, false)
                                    if (ba != null) {
                                        val li = BitmapFactory.decodeStream(ba)
                                        mBuilder.setLargeIcon(getCircularBitmap(li, resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)))
                                    }
                                }
                                val mNotificationManager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                                mNotificationManager.notify(jo.getJSONObject("message").getString("id").toInt(), mBuilder.build())
                            }
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            } else if (tab == MainActivity.SYNC_INTENT) {
                if (newFragment != null) {
                    runOnUiThread { newFragment!!.reset("background", "", "", null) }
                }
            } else if (tab == MainActivity.UPDATE_INTENT) {
                if (newFragment != null) {
                    if (intent.hasExtra("cid")) {
                        if (newFragment is Conv) {
                            (newFragment as Conv).update(intent.getStringExtra("cid"), intent.getStringExtra("conv"))
                        } else {
                            val hm = HashMap<String?, Any?>()
                            hm["cid"] = intent.getStringExtra("cid")
                            newFragment!!.reset("background", "update", "-1", hm)

                        }
                    } else {
                        if (intent.hasExtra("id")) {
                            newFragment!!.reset("background", "update", intent.getStringExtra("id"), null)
                        }
                    }
                }
            } else if (tab == MainActivity.REQUEST_INTENT) {
                if (newFragment != null) {
                    val userinfo = HashMap<String?, Any?>()
                    userinfo["type"] = intent.getIntExtra("type", 0)
                    runOnUiThread { newFragment!!.reset("background", "request", intent.getStringExtra("id"), userinfo) }
                }
            } else if (tab == MainActivity.DREQUEST_INTENT) {
                if (newFragment != null) {
                    val userinfo = HashMap<String?, Any?>()
                    userinfo["type"] = intent.getIntExtra("type", 0)
                    runOnUiThread { newFragment!!.reset("background", "drequest", intent.getStringExtra("id"), userinfo) }
                }
            } else if (tab == MainActivity.CONFIRM_INTENT) {
                if (newFragment != null) {
                    newFragment!!.reset("confirm", intent.getStringExtra("passphrase"), "", null)
                }
            }
            if (tab == MainActivity.SHOW_INTENT) {
                if (intent.hasExtra("conv")) {
                    val conv = intent.getStringExtra("conv")
                    var change = true
                    if (newFragment != null) {
                        if (newFragment is Conv) {
                            if ((newFragment as Conv).conv == conv) {
                                change = false
                            }
                        }
                    }
                    if (change) {
                        val es = Executors.newSingleThreadExecutor()
                        es.execute {
                            if (conv != null) {
                                val lc = getdb().conversationDao()!!.loadAllByfids(conv)
                                if (lc!!.size > 1) {
                                    //log.i("MainActivity","create select account");
                                    val sv = ScrollView(this)
                                    sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                    val ll = LinearLayout(this)
                                    ll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                    ll.orientation = LinearLayout.VERTICAL
                                    val display = this.windowManager.defaultDisplay
                                    val size = Point()
                                    display.getSize(size)
                                    val height = size.y
                                    for (i in lc.indices) {
                                        val c = lc[i]
                                        val acc = ContactManager.getAccountpos(this, c!!.uid)
                                        val info = ContactManager.GetAccountContact(this, acc)
                                        val ll2 = LinearLayout(this)
                                        ll2.orientation = LinearLayout.HORIZONTAL
                                        ll2.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                        ll2.gravity = Gravity.CENTER_VERTICAL
                                        var text: String? = ""
                                        if (info != null) {
                                            text = info[1]
                                            /*if(!info[2].equals("")){
                                                text += " ( "+info[2]+" )";
                                            }*/text += " " + info[2]
                                        }
                                        //log.i("MainActivity","create select account name ="+text);
                                        val tv = TextView(this)
                                        val ba = ContactManager.GetAccountContactPhoto(this, acc, false)
                                        val iv = ImageView(this)
                                        var bm: Bitmap? = null
                                        val gallericon = ResourcesCompat.getDrawable(resources, R.drawable.gallerieicon, null)
                                        if (ba != null) {
                                            bm = BitmapFactory.decodeStream(ba)
                                            if (bm != null) {
                                                bm = Bitmap.createScaledBitmap(bm, 200, 200, false)
                                            } else {
                                                if (gallericon != null) {
                                                    bm = (gallericon as BitmapDrawable).bitmap
                                                }
                                            }
                                        } else {
                                            if (gallericon != null) {
                                                bm = (gallericon as BitmapDrawable).bitmap
                                            }
                                        }
                                        if (bm != null) {
                                            iv.setImageBitmap(bm)
                                        }
                                        var lp = LinearLayout.LayoutParams(height / 10, height / 10, 1f)
                                        lp.setMargins(10, 0, 10, 0)
                                        iv.layoutParams = lp
                                        ll2.addView(iv)
                                        tv.text = text
                                        tv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 20f)
                                        ll2.addView(tv)
                                        val cb = RadioButton(this)
                                        if (i == 0) {
                                            cb.isChecked = true
                                        } else {
                                            cb.isChecked = false
                                        }
                                        cb.id = acc
                                        cb.tag = "rb"
                                        cb.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
                                            if (b) {
                                                for (j in 0 until ll.childCount) {
                                                    val v = ll.getChildAt(j)
                                                    if (v != null) {
                                                        val rb = v.findViewWithTag<RadioButton>("rb")
                                                        if (rb != null) {
                                                            if (compoundButton.id != rb.id) {
                                                                rb.isChecked = false
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                        lp.setMargins(10, 0, 10, 0)
                                        cb.layoutParams = lp
                                        ll2.addView(cb)

                                        ll.addView(ll2)
                                    }

                                    val pbbuttonaction = DialogInterface.OnClickListener { dialog: DialogInterface, which: Int ->
                                        var c: Conversation? = null
                                        for (j in 0 until ll.childCount) {
                                            val v = ll.getChildAt(j)
                                            if (v != null) {
                                                val rb = v.findViewWithTag<RadioButton>("rb")
                                                if (rb != null) {
                                                    if (rb.isChecked) {
                                                        MyApp.selectedaccount = rb.id
                                                        c = lc[j]
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        if (c != null) {
                                            newFragment = Conv.Companion.newInstance(this, c.cid, conv, c.title, c.members, c.status)
                                            val transaction = this.supportFragmentManager.beginTransaction()
                                            transaction.addToBackStack("main")
                                            transaction.replace(R.id.mainlayout, newFragment!!, "dial$conv")
                                            transaction.commit()
                                            dialog.dismiss()
                                        }
                                    }
                                    sv.addView(ll)
                                    runOnUiThread { alert("Select account", null, null, null, "ok", pbbuttonaction, null, null, sv, false) }
                                } else if (lc.size > 0) {
                                    val c = lc[0]
                                    MyApp.selectedaccount = ContactManager.getAccountpos(this, c!!.uid)
                                    newFragment = Conv.Companion.newInstance(this, c.cid, conv, c.title, c.members, c.status)
                                    val transaction = this.supportFragmentManager.beginTransaction()
                                    transaction.addToBackStack("main")
                                    transaction.replace(R.id.mainlayout, newFragment!!, "dial$conv")
                                    transaction.commit()
                                    runOnUiThread { this.supportFragmentManager.executePendingTransactions() }
                                }
                            }
                        }
                        es.shutdown()
                    }
                }
            }
        }
        setIntent(Intent())
    }

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        handleIntent(intent)
    }

    //used to remove account file and database entry linked to an account
    fun removeAccount(acc: Int) {
        val am = AccountManager.get(this)
        val a = am.getAccountsByType(AuthenticatorService.Companion.ACCOUNT_TYPE)
        if (a.size > acc) {
            val uid = ContactManager.GetAccountsID(this, acc)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                am.removeAccount(a[acc], this, { result: AccountManagerFuture<Bundle?>? ->
                    if (newFragment is mainmenupager) {
                        (newFragment as mainmenupager).datachanged()
                    }
                    try {
                        //ContactManager.GetAccountContact();
                        // Get the authenticator result, it is blocking until the
                        // account authenticator completes
                        val es = Executors.newSingleThreadExecutor()
                        es.execute {

                            /*for(File file:getFilesDir().listFiles()){
                                    deletefiles(file);
                                }*/
                            for (c in getdb().conversationDao()!!.getAll(uid)!!) {
                                getdb().conversationDao()!!.delete(c)
                            }
                            for (m in getdb().messageDao()!!.selecttodelete()!!) {
                                val f = File(m!!.mess!!)
                                if (f.exists()) {
                                    if (f.parentFile != null) {
                                        MainActivity.Companion.deletefiles(f.parentFile!!)
                                    }
                                }
                                getdb().messageDao()!!.delete(m)
                            }
                        }
                        es.shutdown()
                        val al = am.getAccountsByType(AuthenticatorService.Companion.ACCOUNT_TYPE).size
                        if (al == 0) {
                            val pushIntent = Intent(this@MainActivity, SignalService::class.java)
                            pushIntent.action = "stop"
                            pushIntent.putExtra("stop", true)
                            startService(pushIntent)
                            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(pushIntent);
                            }
                            else{
                                startService(pushIntent);
                            }*/
                            val i = Intent(this@MainActivity, MainActivity::class.java)
                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(i)
                        } else {
                            if (MyApp.selectedaccount >= al) {
                                MyApp.selectedaccount = 0
                            }
                            if (newFragment is mainmenupager) {
                                (newFragment as mainmenupager).datachanged()
                            }
                        }
                        //finish();
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, null)
            } else {
                am.removeAccount(a[acc], { result: AccountManagerFuture<Boolean?> ->
                    try {
                        if (newFragment is mainmenupager) {
                            (newFragment as mainmenupager).datachanged()
                        }
                        val es = Executors.newSingleThreadExecutor()
                        es.execute {

                            /*for(File file:getFilesDir().listFiles()){
                                    deletefiles(file);
                                }*/
                            for (c in getdb().conversationDao()!!.getAll(uid)!!) {
                                getdb().conversationDao()!!.delete(c)
                            }
                            for (m in getdb().messageDao()!!.selecttodelete()!!) {
                                val f = File(m!!.mess!!)
                                if (f.exists()) {
                                    if (f.parentFile != null) {
                                        deletefiles(f.parentFile!!)
                                    }
                                }
                                getdb().messageDao()!!.delete(m)
                            }
                        }
                        es.shutdown()
                        // Get the authenticator result, it is blocking until the
                        // account authenticator completes
                        val al = am.getAccountsByType(AuthenticatorService.Companion.ACCOUNT_TYPE).size
                        if (al == 0) {
                            result.result
                            val pushIntent = Intent(this@MainActivity, SignalService::class.java)
                            pushIntent.action = "stop"
                            pushIntent.putExtra("stop", true)
                            startService(pushIntent)
                            val i = Intent(this@MainActivity, MainActivity::class.java)
                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(i)
                        } else {
                            if (MyApp.selectedaccount >= al) {
                                MyApp.selectedaccount = 0
                            }
                            if (newFragment is mainmenupager) {
                                (newFragment as mainmenupager).datachanged()
                            }
                        }

                        //finish();
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, null)
            }
        }
        val params = Bundle()
        params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        ContentResolver.requestSync(null, ContactsContract.AUTHORITY, params)
    }

    //used to manage multiple account (not in use)
    override fun onAccountChanged() {
        val am = AccountManager.get(this)
        val a = am.getAccountsByType(AuthenticatorService.Companion.ACCOUNT_TYPE)
        val al = a.size
        if (al == 0) {
            val pushIntent = Intent(this@MainActivity, SignalService::class.java)
            pushIntent.action = "stop"
            pushIntent.putExtra("stop", true)
            startService(pushIntent)
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(pushIntent  );
            }
            else{
                startService(pushIntent);
            }*/
            val i = Intent(this@MainActivity, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(i)
        } else {
            if (newFragment is mainmenupager) {
                (newFragment as mainmenupager).datachanged()
            }
        }
    }

    override fun needsupdate(intent: Intent) {
        handleIntent(intent)
    }

    //companion object for static access to variable and function
    companion object {
        private var csd: ConvDownloaderListener? = null
        private var loginsuccess = false
        private var ll: LoginListener? = null
        const val SYNC_INTENT = 0
        const val UPDATE_INTENT = 1
        const val REQUEST_INTENT = 2
        const val CONFIRM_INTENT = 4
        const val MSG_INTENT = 5
        const val SHOW_INTENT = 7
        const val DREQUEST_INTENT = 8
        val status = intArrayOf(R.string.available, R.string.away, R.string.dnd, R.string.invisibledisconnected)
        fun setConvDownloaderListener(convDownloaderListener: ConvDownloaderListener?) {
            MainActivity.csd = convDownloaderListener
        }

        fun onfinishdownload(timestamp: Int, conv: String?, num: Int): Boolean {
            //log.i("MyApp","onfinishdownload");
            return if (MainActivity.csd != null) {
                //log.i("MyApp","csd != null");
                MainActivity.csd!!.onFinish(timestamp, conv!!, num)
            } else {
                false
            }
        }

        fun setLoginListener(ll: LoginListener?) {
            MainActivity.ll = ll
            if (loginsuccess) {
                loginsuccess = false
                loginSuccess()
            }
        }

        fun loginSuccess() {
            if (MainActivity.ll != null) {
                MainActivity.ll!!.LoginSucces()
            }
        }

        fun loginError(msg: String?) {
            if (MainActivity.ll != null) {
                MainActivity.ll!!.error(msg)
            }
        }

        fun deletefiles(f: File) {
            if (f.exists()) {
                if (f.isDirectory) {
                    val files = f.listFiles()
                    if (files != null) {
                        for (file in files) {
                            MainActivity.deletefiles(file)
                        }
                    }
                }
                if (!f.delete()) {
                    MainActivity.loginError("file error")
                }
            }
        }
    }

    override fun result(msg: String) {

    }

    override fun error(msg: String?) {

    }
}