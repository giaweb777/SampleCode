package com.miraicall.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract.Settings;
import android.util.Log;


import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/*
 * Sync1 = status ( 1: online,2:busy,3:dnd,4:invisible/disconnected)
 * Sync2 = Account id
 * Sync3 = timestamp
 * Sync4 = callstatus
 * */

/*
	Contact Manager Class
	contains static methods used to manipulate contact
 */
public class ContactManager {

	public ContactManager() {

	}
	//call status values
	public static final int STATUS_IDLE = 0;
	public static final int STATUS_CLOSED = 1;
	public static final int STATUS_CONNECTING = 2;
	public static final int STATUS_CONNECTED = 3;


	static void resetCallstatus(Context c) throws OperationApplicationException, RemoteException {
		ArrayList<ContentProviderOperation> ops =
				new ArrayList<>();
		ops.add(ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withSelection( RawContacts.ACCOUNT_TYPE + " = '" + AuthenticatorService.ACCOUNT_TYPE + "'", null)
				.withValue(RawContacts.SYNC4, STATUS_IDLE)
				.build());


		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
	}
	/*
		Set contact call status to idle if current status is closed
	 */
	static void removeallclosed(Context c) throws OperationApplicationException, RemoteException {
		ArrayList<ContentProviderOperation> ops =
				new ArrayList<>();
		ops.add(ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withSelection(RawContacts.SYNC4 + " = '" + STATUS_CLOSED + "' AND " + RawContacts.ACCOUNT_TYPE + " = '" + AuthenticatorService.ACCOUNT_TYPE + "'", null)
				.withValue(RawContacts.SYNC4, STATUS_IDLE)
				.build());


		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
	}

	/*
		Set contact call status
		sid : the id of the contact to update
		status : the new call status of the contact
	 */
	static void setCallStatus(Context c, String sid,int status) throws OperationApplicationException, RemoteException {
		ArrayList<ContentProviderOperation> ops =
				new ArrayList<>();
		ops.add(ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withSelection(RawContacts.SOURCE_ID + " = '" + sid + "' AND " + RawContacts.ACCOUNT_TYPE + " = '" + AuthenticatorService.ACCOUNT_TYPE + "'", null)
				.withValue(RawContacts.SYNC4, status)
				.build());


		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
	}

	/*
		get contact call status
		sid : the id of the contact to update

	 */
	static int getCallStatus(Context co, String sid)  {
		int res = STATUS_IDLE;

		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_TYPE )
				.build();
		Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SYNC4}, RawContacts.SOURCE_ID+" = '"+sid+ "' GROUP BY "+RawContacts.SOURCE_ID, null, null );

		if (c != null) {
			if (c.moveToNext()) {
				for(int i = 0; i< c.getColumnCount();i++){
					if(!c.isNull(i)){
						res = c.getInt(i);

					}
				}
			}
			c.close();
		}



		return res;
	}

	/*
		get ids of friend  with call status > 0
		return a HashMap with containing the ids and the status corresponding
	 */
	static HashMap<String,Integer> getCallStatusfids(Context co) {
		HashMap<String,Integer> res = new HashMap<>();

		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.build();
		Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID,RawContacts.SYNC4}, RawContacts.SYNC4+" > 0 AND "+ RawContacts.ACCOUNT_TYPE +" = '"+AuthenticatorService.ACCOUNT_TYPE+"'", null, null );

		if (c != null) {
			while (c.moveToNext()) {
				res.put(c.getString(0),c.getInt(1));
			}
			c.close();
		}
		return res;
	}

	/*
		method used to check if a call is initiated
	 */
	static boolean isCalling(Context co) {


		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_TYPE )
				.build();
		Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID}, RawContacts.SYNC4+" > 0 GROUP BY "+RawContacts.SOURCE_ID, null, null );
		boolean res = false;
		if (c != null) {
			if(c.moveToNext()){
				res = true;
			}
			c.close();
		}
		return res;
	}

	/*
		method used to add a contact
		parameters:
	 	c : the app context
		acc : the account to link the contact with
		sid : the id of the friends on the remote service
		name : the name of the contact
		firstname  : firtsname of the contact
		nickname : nickname of the contact
		number : the phone number
		email : the email
		time : the timestamp used for syncing
		photop : the profile pic as a byte array (blob)
	 */
	static void addContact(Context c,Account acc,String sid,String name,String firstname,String nickname,String number,String email,String time,byte[] photop) throws RemoteException, OperationApplicationException{
		AccountManager am = AccountManager.get(c);


		ArrayList<ContentProviderOperation> ops =
				new ArrayList<>();
		int rawContactInsertIndex = ops.size();

		ops.add(ContentProviderOperation.newInsert(ContactsContract.Settings.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValue(Settings.ACCOUNT_NAME, acc.name)
				.withValue(Settings.ACCOUNT_TYPE, acc.type)
				.withValue(Settings.UNGROUPED_VISIBLE, 1)
				.build());
		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		ops.clear();

		//link contact with the account
		//set source id and syncing parameters
		ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValue(RawContacts.ACCOUNT_NAME, acc.name )
				.withValue(RawContacts.ACCOUNT_TYPE, acc.type)
				.withValue(RawContacts.SOURCE_ID, sid)
				.withValue(RawContacts.SYNC1, 4)
				.withValue(RawContacts.SYNC2,0)
				.withValue(RawContacts.SYNC3,time)
				.withValue(RawContacts.SYNC4,STATUS_IDLE)
				.build());
		//add the name,firstname and nickname
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
				.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
				.withValue(StructuredName.FAMILY_NAME, name)
				.withValue(StructuredName.GIVEN_NAME, firstname)
				.withValue(StructuredName.MIDDLE_NAME, nickname)
				.build());
		//add the phone number as home number
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
				.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
				.withValue(Phone.NUMBER, number)
				.withValue(Phone.TYPE, Phone.TYPE_HOME)
				.build());
		//add the email address
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
				.withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
				.withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
				.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
				.build());
		//add the profilepic
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
				.withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
				.withValue(Photo.PHOTO,photop)
				.build());


		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);

		//cancel friend request notification if exists
		NotificationManager mNotificationManager =
				(NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
		if (mNotificationManager != null) {
			mNotificationManager.cancel(Integer.valueOf(sid));
		}

		Long mainid = ContactManager.getConntact_ID(c,acc.name,acc.type,sid);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		//check if contact exist on other account and aggregate them so they don't display
		//twice on contact list
		for(int i=0;i<a.length;i++){
			if(!a[i].name.equals(acc.name)){
				if(ContactManager.ContactExist(c,i,sid)){
					ops = new ArrayList<>();
					long secondid = ContactManager.getConntact_ID(c,a[i].name,a[i].type,sid);
					ops.add(ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
							.withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
							.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, mainid )
							.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, secondid).build());
					c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
				}
			}

		}



	}


	/*
		update the sync time of a contact
		acc : account synced
		sid : friend id
		time : the new sync time
	 */
	static void updatesync(Context c,Account acc,String sid,String time) throws RemoteException, OperationApplicationException{

		ArrayList<ContentProviderOperation> ops =
				new ArrayList<>();
		ops.add(ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withSelection(RawContacts.SOURCE_ID + " = '" + sid + "' AND " + RawContacts.ACCOUNT_TYPE + " = '" + acc.type + "' AND " + RawContacts.ACCOUNT_NAME + " = '" + acc.name + "'", null)
				.withValue(RawContacts.SYNC3, time)
				.build());


		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);


	}

	/*
		return the account for a specific id
		sid : the id of the account to be returned
	 */
	static Account getAccount(Context c,String sid) {



		Cursor curs = c.getContentResolver().query(RawContacts.CONTENT_URI,new String[]{RawContacts.ACCOUNT_NAME}, RawContacts.SYNC2 +"= '1' AND "+ RawContacts.SOURCE_ID +" = '"+sid+ "' AND "+ RawContacts.ACCOUNT_TYPE +" = '"+AuthenticatorService.ACCOUNT_TYPE+ "'", null, null );
		String res;
		if (curs != null) {
			if (curs.moveToNext()) {
				if(!curs.isNull(0)){
					res = curs.getString(0);
					AccountManager am = AccountManager.get(c);
					Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
					for (Account account : a) {
						if (account.name.equals(res)) {

							curs.close();
							return account;
						}

					}

				}

			}
			curs.close();
		}



		return null;


	}

	/*
		get the position of the account in the account Manager array
		return -1 if account not found
	 */
    static int getAccountpos(Context c,String sid) {



        Cursor curs = c.getContentResolver().query(RawContacts.CONTENT_URI,new String[]{RawContacts.ACCOUNT_NAME}, RawContacts.SYNC2 +"= '1' AND "+ RawContacts.SOURCE_ID +" = '"+sid+ "' AND "+ RawContacts.ACCOUNT_TYPE +" = '"+AuthenticatorService.ACCOUNT_TYPE+ "'", null, null );
        String res;
        if (curs != null) {
            if (curs.moveToNext()) {
                if(!curs.isNull(0)){
                    res = curs.getString(0);
                    AccountManager am = AccountManager.get(c);
                    Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
                    for (int i=0;i<a.length;i++) {
                        if (a[i].name.equals(res)) {

                            curs.close();
                            return i;
                        }

                    }

                }

            }
            curs.close();
        }



        return -1;


    }

    /*
    	Create the contact linked with the account ( you )
    	sid : your id on the service
    	username : your username for this account
    	name : your name
    	firstname : your firstname
    	number : your phonenumber
    	email : your email
    	random : a random generated string used for authentication
    	photop : your profile picture as a byte array
     */
	static void CreateAccountContact(Context c,String sid,String username,String name,String firstname,String number,String email, String random,byte[] photop) throws OperationApplicationException, RemoteException {

		// create the account
		AccountManager am = AccountManager.get(c);
		final Account account = new Account(username, AuthenticatorService.ACCOUNT_TYPE);
		am.addAccountExplicitly(account, random, null);

		//set the sync time as 0 since it's just created
		am.setUserData(account,"time","0");
		ArrayList<ContentProviderOperation> ops =
				new ArrayList<>();
		int rawContactInsertIndex = ops.size();
		ops.add(ContentProviderOperation.newInsert(ContactsContract.Settings.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValue(Settings.ACCOUNT_NAME, account.name )
				.withValue(Settings.ACCOUNT_TYPE, account.type)
				.withValue(Settings.UNGROUPED_VISIBLE, 1)
				.build());
		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		ops.clear();

		//link contact to account and add source id and sync parameters
		ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValue(RawContacts.ACCOUNT_NAME, account.name )
				.withValue(RawContacts.ACCOUNT_TYPE, account.type)
				.withValue(RawContacts.SOURCE_ID, sid)
				.withValue(RawContacts.SYNC1, 4)
				.withValue(RawContacts.SYNC2,1)
				.withValue(RawContacts.SYNC3,"0")
				.withValue(RawContacts.SYNC4,STATUS_IDLE)
				.build());
		//add name and firstname
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
				.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
				.withValue(StructuredName.FAMILY_NAME, name)
				.withValue(StructuredName.GIVEN_NAME, firstname)

				.build());
		//add phone number
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
				.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
				.withValue(Phone.NUMBER, number)
				.withValue(Phone.TYPE, Phone.TYPE_HOME)
				.build());
		//add email address
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
				.withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
				.withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
				.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
				.build());
		//add profile picture
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
				.withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
				.withValue(Photo.PHOTO,photop)
				.build());

		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		Long mainid = ContactManager.getConntact_ID(c,account.name,account.type,sid);
		// agregate with other contact if multiple account ( should never append in normal use)
		for(int i=0;i<a.length;i++){
			if(!a[i].name.equals(account.name)){
				if(ContactManager.ContactExist(c,i,sid)){
					ops = new ArrayList<>();
					long secondid = ContactManager.getConntact_ID(c,a[i].name,a[i].type,sid);
					ops.add(ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
							.withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
							.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, mainid )
							.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, secondid).build());
					c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
				}
			}

		}
		// add authomatic sync
		ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
		Bundle params = new Bundle();
		params.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, false);
		params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
		// set frequency (1h -> 3600s)
		ContentResolver.addPeriodicSync(account, ContactsContract.AUTHORITY, params, 3600);
		ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);


	}

	/*
		get the account profile pic
	 */
	 static InputStream GetAccountContactPhoto(Context co,int acc,boolean highres){
		AccountManager am = AccountManager.get(co);
		 Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		 if(a.length > acc){
			 Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					 .appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					 .appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					 .build();
			 Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContactsEntity.CONTACT_ID}, RawContacts.SYNC2 +"= '1' AND "+ Data.MIMETYPE +" = '"+Photo.CONTENT_ITEM_TYPE + "'", null, null );
			 InputStream res = null;

			long cid = -1;
			 if (c != null) {
				 while ( c.moveToNext()) {
					 cid = c.getLong(0);
				 }
				 c.close();
			 }
			 if(cid != -1){

				 Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cid);

				 res = ContactsContract.Contacts.openContactPhotoInputStream(co.getContentResolver(),contactUri,highres);

			 }
			 Log.i("ContactManager","res = "+res);
			 return res;
		 }
		 return null;

	}

	/*
		Get contact id in the android directory
		Used for aggregation
		aname : account name
		atype : account type
		sid : contact id on the service
	 */
	private static long getConntact_ID(Context co, String aname, String atype, String sid){

		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, aname)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, atype)
				.build();
		Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts._ID},  RawContacts.SOURCE_ID + "= '" + sid + "'", null, null );
		long res = -1;


		if (c != null) {
			while ( c.moveToNext()) {
				for(int i = 0; i< c.getColumnCount();i++){
					if(!c.isNull(i)){
						res = c.getLong(i);
					}
				}
			}
			c.close();
		}
		return res;

	}

	/*
		Get The contact profile pic
		acc : the account to search the contact
		id : the id of the contact on the service
		highres : quality of the picture

	 */
	static InputStream GetContactPhoto(Context co, int acc, String id, boolean highres){
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);

		if(a.length > acc){
			Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					.build();
			Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.CONTACT_ID}, RawContacts.SOURCE_ID +"= '"+id+"'", null, null );
			InputStream res = null;
			long cid = -1;
			if (c != null) {
				while (c.moveToNext()) {
					cid = c.getLong(0);

				}
				c.close();
			}
			//cid = 387;
			if(cid != -1){
				Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cid);
				res = ContactsContract.Contacts.openContactPhotoInputStream(co.getContentResolver(),contactUri,highres);

			}
			return res;
		}
		return null;
	}



	/*
		return the Account information as array of string
		0:sid,1:Firstname,2:Name,3:nickname ( middle name),4:status,5:phone,6:email
	 */
	static String[] GetAccountContact(Context co,int acc){
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);

		if(a.length >acc){
			String[] res = new String[7];
			Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					.build();

			Cursor c = co.getContentResolver().query(rawContactUri, new String[]{ RawContacts.SOURCE_ID, RawContactsEntity.DATA2, RawContactsEntity.DATA3,RawContactsEntity.DATA5,RawContacts.SYNC1}, RawContacts.SYNC2 + "= '1' AND " + Data.MIMETYPE + " = '" + StructuredName.CONTENT_ITEM_TYPE + "'", null, null);
			Cursor c2 = co.getContentResolver().query(rawContactUri, new String[]{RawContactsEntity.DATA1}, RawContacts.SYNC2 + "= '1' AND " + Data.MIMETYPE + " = '" + Phone.CONTENT_ITEM_TYPE + "'", null, null);
			Cursor c3 = co.getContentResolver().query(rawContactUri, new String[]{RawContactsEntity.DATA1}, RawContacts.SYNC2 + "= '1' AND " + Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'", null, null);

			int resi = 0;
			if (c != null) {
				if (c.moveToNext()) {
					for(int i = 0; i< c.getColumnCount();i++){
						if(!c.isNull(i)){
							res[resi] = c.getString(i);
							resi++;
						}
						else{
							res[resi] = "";
							resi++;
						}
					}
				}
				c.close();
			}
			if (c2 != null) {
				if (c2.moveToNext()) {
					for(int i = 0; i< c2.getColumnCount();i++){
						if(!c2.isNull(i)){
							res[resi] = c2.getString(i);
							resi++;
						}
						else{
							res[resi] = "";
							resi++;
						}
					}
				}
				c2.close();
			}
			if (c3 != null) {
				if (c3.moveToNext()) {
					for(int i = 0; i< c3.getColumnCount();i++){
						if(!c3.isNull(i)){
							res[resi] = c3.getString(i);
							resi++;
						}
						else{
							res[resi] = "";
							resi++;
						}
					}
				}
				c3.close();
			}
			if(resi != 0){

				return res;
			}
		}
		return null;
	}

	/*
            return the contact information as array of string
            acc: the account on which the contact is linked
            id: the id of the contact on the remote service
            0:Firstname,1:Name,2:nickname ( middle name),3:phone,4:email
         */
	static String[] GetContactInfo(Context co,int acc,String id){
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		if(a.length > acc){
			Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					.build();

			Cursor c = co.getContentResolver().query(rawContactUri, new String[]{RawContactsEntity.DATA2, RawContactsEntity.DATA3, RawContactsEntity.DATA5}, RawContacts.SYNC2 + "= '0' AND " + RawContacts.SOURCE_ID + "= '" + id + "' AND " + Data.MIMETYPE + " = '" + StructuredName.CONTENT_ITEM_TYPE + "'", null, null);
			Cursor c2 = co.getContentResolver().query(rawContactUri, new String[]{RawContactsEntity.DATA1}, RawContacts.SYNC2 + "= '0' AND " + RawContacts.SOURCE_ID + "= '" + id + "' AND " + Data.MIMETYPE + " = '" + Phone.CONTENT_ITEM_TYPE + "'", null, null);
			Cursor c3 = co.getContentResolver().query(rawContactUri, new String[]{RawContactsEntity.DATA1}, RawContacts.SYNC2 + "= '0' AND " + RawContacts.SOURCE_ID + "= '" + id + "' AND " + Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'", null, null);

			String[] res = new String[5];
			int resi = 0;
			if (c != null) {
				while (c.moveToNext()) {
					for(int i = 0; i< c.getColumnCount();i++){
						if(!c.isNull(i)){
							res[resi] = c.getString(i);
							resi++;

						}
						else{
							res[resi] = "";
							resi++;
						}
					}
				}
				c.close();
			}
			if (c2 != null) {
				while (c2.moveToNext()) {
					for(int i = 0; i< c2.getColumnCount();i++){
						if(!c2.isNull(i)){
							res[resi] = c2.getString(i);
							resi++;
						}
						else{
							res[resi] = "";
							resi++;
						}
					}
				}
				c2.close();
			}
			if (c3 != null) {
				if (c3.moveToNext()) {
					for(int i = 0; i< c3.getColumnCount();i++){
						if(!c3.isNull(i)){
							res[resi] = c3.getString(i);
							resi++;
						}
						else{
							res[resi] = "";
							resi++;
						}
					}
				}
				c3.close();
			}
			if(resi >0){
				return res;
			}


		}
		return null;
	}



	/*
		Get the id on the remote service of the selected account
		in the AccountManager array.
	
	 */
	static String GetAccountsID(Context co,int selectedaccount){
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		String res = "-1";
		if(a.length > selectedaccount && selectedaccount > -1 ){
			Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[selectedaccount].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[selectedaccount].type)
					.build();
			Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID}, RawContacts.SYNC2 +"= '1' AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "'", null, null );

			if (c != null) {
				while (c.moveToNext()) {
					for(int i = 0; i< c.getColumnCount();i++){
						if(!c.isNull(i)){
							res = c.getString(i);

						}
					}
				}
				c.close();
			}
		}


		return res;
	}

	/*
		Get the id on the remote service of the selected account
	 */
	static String GetAccountsID(Context co,Account acc){
		String res = "-1";

		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, acc.name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, acc.type)
				.build();
		Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID}, RawContacts.SYNC2 +"= '1' AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "'", null, null );

		if (c != null) {
			while (c.moveToNext()) {
				for(int i = 0; i< c.getColumnCount();i++){
					if(!c.isNull(i)){
						res = c.getString(i);

					}
				}
			}
			c.close();
		}



		return res;
	}

	/*
		Get the status of the user of the selected account
	 */
	static int GetAccountsStatus(Context co,Account acc){
		int res = -1;

		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, acc.name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, acc.type)
				.build();
		Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SYNC1}, RawContacts.SYNC2 +"= '1' AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "'", null, null );

		if (c != null) {
			while (c.moveToNext()) {
				for(int i = 0; i< c.getColumnCount();i++){
					if(!c.isNull(i)){
						res = c.getInt(i);

					}
				}
			}
			c.close();
		}



		return res;
	}

	/*
		retrieve device password ( specific to application)
	 */
	public static String GetPassw(Context co){
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		if(a.length >0){

			return am.getPassword(a[0]);
		}
		else{
			return "";
		}
	}

	/*
		check if a contact exist
		 acc:account
		 sid:the contact sid on the remote service
	 */
	static boolean ContactExist(Context co,int acc,String sid){
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		boolean res = false ;
		if(a.length > acc){
			Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					.build();
			Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID}, RawContacts.SOURCE_ID +"='"+sid+"' AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "'", null, null );

			if (c != null) {
				res = c.moveToNext();
				c.close();
			}
		}


		return res;
	}

	/*
		check if a contact exist
		 acc:account
		 sid:the contact sid on the remote service
	 */
	static boolean ContactExist(Context co,Account acc,String sid){
		boolean res = false ;
		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, acc.name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, acc.type)
				.build();
		Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID}, RawContacts.SOURCE_ID +"='"+sid+"' AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "'", null, null );

		if (c != null) {
			res = c.moveToNext();
			c.close();
		}



		return res;
	}


	/*
           update the contact linked with the account ( you )
           id : id on the service
           name : new name of the contact
           firstname : new firstname
           nickname : new nickname
           number : new phone number
           email : new email address
           time : timestamp of the update
           photop : your profile picture as a byte array
        */
	static void UpdateContact(Context c,Account acc,String id,String name,String firstname,String nickname,String number,String email,String time,byte[] photop) throws RemoteException, OperationApplicationException{

		ArrayList<ContentProviderOperation> ops =
				new ArrayList<>();

		ops.add(ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withSelection(RawContacts.SOURCE_ID + " = '"+id+"' AND "+RawContacts.ACCOUNT_TYPE +" = '"+acc.type+"' AND "+RawContacts.ACCOUNT_NAME +" = '"+acc.name+"'",null)
				.withValue(RawContacts.SYNC3, time)

				.build());
		// update name,firstname, nickname of the contact
		ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withSelection(RawContacts.SOURCE_ID + " = '"+id+"' AND " + Data.MIMETYPE +" = '" +StructuredName.CONTENT_ITEM_TYPE+"' AND "+RawContacts.ACCOUNT_TYPE +" = '"+acc.type+"' AND "+RawContacts.ACCOUNT_NAME +" = '"+acc.name+"'",null)
				.withValue(StructuredName.FAMILY_NAME, name)
				.withValue(StructuredName.GIVEN_NAME,firstname)
				.withValue(StructuredName.MIDDLE_NAME, nickname)
				.build());
		// update phone number of the contact
		ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withSelection(RawContacts.SOURCE_ID + " = '"+id+"' AND " + Data.MIMETYPE +" = '" +Phone.CONTENT_ITEM_TYPE+"' AND "+RawContacts.ACCOUNT_TYPE +" = '"+acc.type+"' AND "+RawContacts.ACCOUNT_NAME +" = '"+acc.name+"'",null)
				.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
				.withValue(Phone.NUMBER, number)
				.withValue(Phone.TYPE, Phone.TYPE_HOME)
				.build());

		// check if contact has an email address
		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, acc.name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, acc.type)
				.build();
		Cursor c1 = c.getContentResolver().query(rawContactUri, new String[]{RawContactsEntity.DATA1}, RawContacts.SOURCE_ID + "= '" + id + "' AND " + Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'", null, null);
		Log.i("CM","cl "+c1);

		if(c1 != null){

			if (c1.getCount() > 0) {
				// email address found update email address

				ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
						.withSelection(RawContacts.SOURCE_ID + " = '"+id+"' AND " + Data.MIMETYPE +" = '" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE+"' AND "+RawContacts.ACCOUNT_TYPE +" = '"+acc.type+"' AND "+RawContacts.ACCOUNT_NAME +" = '"+acc.name+"'",null)
						.withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
						.withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
						.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
						.build());
			}
			else{
				// email not found, create new email address
				Cursor c2 = c.getContentResolver().query(rawContactUri, new String[]{RawContacts._ID}, RawContacts.SOURCE_ID + "= '" + id + "'", null, null);
				if(c2 != null){
					c2.moveToFirst();
					if(!c2.isNull(0)){
						ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
								.withValue(Data.RAW_CONTACT_ID, c2.getInt(0))
								.withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
								.withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
								.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
								.build());
					}

				}
			}
			c1.close();

		}

		if(photop != null){

			//update profile pic

			ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
					.withSelection(RawContacts.SOURCE_ID + " = '"+id+"' AND " + Data.MIMETYPE +" = '" +Photo.CONTENT_ITEM_TYPE+"' AND "+RawContacts.ACCOUNT_TYPE +" = '"+acc.type+"' AND "+RawContacts.ACCOUNT_NAME +" = '"+acc.name+"'",null)
					.withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
					.withValue(Photo.PHOTO,photop)
					.build());


		}
		c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);


	}

	/*
	 	update contact status
	 	acc : account the contact is linked with
	 	id : the id of the contact on the remote service
	 	status : the new status
	 */

	static void UpdateContactStatus (Context c,int acc,String id,int status) throws RemoteException, OperationApplicationException{
		AccountManager am = AccountManager.get(c);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		Log.i("Contact Manager","update status "+id+" status = "+status);
		if(a.length > acc){
			ArrayList<ContentProviderOperation> ops =
					new ArrayList<>();

			ops.add(ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
					.withSelection(RawContacts.SOURCE_ID + " = '"+id+"' AND "+RawContacts.ACCOUNT_TYPE +" = '"+a[acc].type+"' AND "+RawContacts.ACCOUNT_NAME +" = '"+a[acc].name+"'",null)
					.withValue(RawContacts.SYNC1, status)
					.build());
			c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		}

	}

	/*
		set all status to 4 ( disconnected)
		used when signaling service connection is lost
	 */
	static void disconnected(Context c) throws RemoteException, OperationApplicationException{
		AccountManager am = AccountManager.get(c);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);

		for (Account account : a) {
			ArrayList<ContentProviderOperation> ops =
					new ArrayList<>();
			ops.add(ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
					.withSelection(RawContacts.ACCOUNT_TYPE + " = '" + account.type + "' AND " + RawContacts.ACCOUNT_NAME + " = '" + account.name + "'", null)
					.withValue(RawContacts.SYNC1, 4)
					.build());
			c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		}

	}

	/*
		Get Contact count
		acc : the account contact are linked with
		filter : filter string used for search on contact

		this method is used by recycler view to make contact list
	 */
	static int getCount(Context co,int acc,String filter){
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		filter = DatabaseUtils.sqlEscapeString(filter);
		filter = filter.substring(1,filter.length() -1);
		Log.i("cm","filter = "+filter);
		//filter = filter.replaceAll("'", "''");
		if(a.length > acc){
			Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					.build();
			String[] filters = filter.split("\\s+");
			StringBuilder f = new StringBuilder();
			for(int i = 0; i < filters.length;i++){
				if(i>0)
					f.append("AND (").append(RawContactsEntity.DATA2).append(" LIKE '%").append(filters[i]).append("%' OR ").append(RawContactsEntity.DATA5).append(" LIKE '%").append(filters[i]).append("%' OR ").append(RawContactsEntity.DATA3).append(" LIKE '%").append(filters[i]).append("%')");
				else f.append("(").append(RawContactsEntity.DATA2).append(" LIKE '%").append(filters[i]).append("%' OR ").append(RawContactsEntity.DATA5).append(" LIKE '%").append(filters[i]).append("%' OR ").append(RawContactsEntity.DATA3).append(" LIKE '%").append(filters[i]).append("%')");
			}

			Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID,RawContactsEntity.DATA2,RawContactsEntity.DATA3}, RawContacts.SYNC2 +"= '0' AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "' AND ("+f+" ) " , null, RawContactsEntity.SYNC1+" ASC,"+RawContactsEntity.DATA2 + " ASC" );
			if(c != null){
				int count = c.getCount();
				c.close();
				return count;
			}
		}
		return 0;

	}

	/*
		Get Contact at a given position
		acc : the account contact are linked with
		filter : filter string used for search on contact
		pos : position of the contact
		return array list : 0:id,1:Firstname,2:Name,3:nickname ( middle name),4:phone,5:email
		this method is used by recycler view to make contact list
	 */
	static String[] getContactAt(Context co,int acc,String filter,int pos){
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		filter = DatabaseUtils.sqlEscapeString(filter);
		filter = filter.substring(1,filter.length() -1);
		Log.i("cm","filter = "+filter);
		if(a.length > acc){
			Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					.build();
			String[] filters = filter.split("\\s+");
			StringBuilder f = new StringBuilder();
			for(int i = 0; i < filters.length;i++){
				if(i>0)
					f.append("AND (").append(RawContactsEntity.DATA2).append(" LIKE '%").append(filters[i]).append("%' OR ").append(RawContactsEntity.DATA5).append(" LIKE '%").append(filters[i]).append("%' OR ").append(RawContactsEntity.DATA3).append(" LIKE '%").append(filters[i]).append("%')");
				else f.append("(").append(RawContactsEntity.DATA2).append(" LIKE '%").append(filters[i]).append("%' OR ").append(RawContactsEntity.DATA5).append(" LIKE '%").append(filters[i]).append("%' OR ").append(RawContactsEntity.DATA3).append(" LIKE '%").append(filters[i]).append("%')");
			}
			Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID,RawContactsEntity.DATA2,RawContactsEntity.DATA3,RawContactsEntity.DATA5,RawContacts.SYNC1}, RawContacts.SYNC2 +"= '0' AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "' AND ("+f+")" , null, RawContacts.SYNC1+" ASC,"+RawContactsEntity.DATA2 + " ASC LIMIT 1 OFFSET "+pos );

			if(c != null){
				String[] res = new String[7];
				while (c.moveToNext()) {

					for(int i = 0; i< c.getColumnCount();i++){
						if(!c.isNull(i)){
							res[i] = c.getString(i);
						}
					}
					Cursor c2 = co.getContentResolver().query(rawContactUri, new String[]{RawContactsEntity.DATA1}, RawContacts.SYNC2 + "= '0' AND " + RawContacts.SOURCE_ID + "= '" + res[0] + "' AND " + Data.MIMETYPE + " = '" + Phone.CONTENT_ITEM_TYPE + "'", null, null);
					if (c2 != null) {
						while (c2.moveToNext()) {
							if(!c2.isNull(0)){
								res[5] = c2.getString(0);
							}
							else{
								res[5] = "";
							}
						}
						c2.close();
					}
					Cursor c3 = co.getContentResolver().query(rawContactUri, new String[]{RawContactsEntity.DATA1}, RawContacts.SYNC2 + "= '0' AND " + RawContacts.SOURCE_ID + "= '" + res[0] + "' AND " + Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'", null, null);
					if (c3 != null) {
						if (c3.moveToNext()) {
							for(int i = 0; i< c3.getColumnCount();i++){
								if(!c3.isNull(i)){
									res[6] = c3.getString(i);
								}
								else{
									res[6] = "";
								}
							}
						}
						c3.close();
					}


				}
				c.close();
				return res;
			}
		}
		return null;


	}

	/*
		Get contact list with map containing fid: the id on the remote service, and 0 default status
		used to make list with checkbox status for example whe checked we just have to put status to 1
	 */
	static ArrayList<Map.Entry<String,Integer>> GetContactList(Context co,int acc) {
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		if(a.length > acc){
			Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					.build();
			Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID}, RawContacts.SYNC2 +"= '0' AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "'" , null, RawContactsEntity.DATA2 + " ASC" );

			if (c != null) {
				ArrayList<Map.Entry<String,Integer>> result = new ArrayList<>();
				while (c.moveToNext()) {
					for(int i = 0; i< c.getColumnCount();i++){
						if(!c.isNull(i)){
							result.add(new AbstractMap.SimpleEntry<>(c.getString(i), 0));
						}
					}

				}
				c.close();
				return result;
			}


		}
		return null;

	}

	/*
		Get contact list with map containing fid: the id on the remote service, and status

	 */
	static ArrayList<Map.Entry<String,Integer>> GetOnlineContactList(Context co,int acc) {
		AccountManager am = AccountManager.get(co);
		Account[] a = am.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		if(a.length > acc){
			Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
					.appendQueryParameter(RawContacts.ACCOUNT_NAME, a[acc].name)
					.appendQueryParameter(RawContacts.ACCOUNT_TYPE, a[acc].type)
					.build();
			Cursor c = co.getContentResolver().query(rawContactUri,new String[]{RawContacts.SOURCE_ID,RawContacts.SYNC1}, RawContacts.SYNC2 +"= '0' AND "+RawContacts.SYNC1 +"<> '4' AND "+RawContacts.SYNC4 +"="+STATUS_IDLE+" AND "+ Data.MIMETYPE +" = '"+StructuredName.CONTENT_ITEM_TYPE + "'" , null, RawContacts.SYNC1+" ASC,"+RawContactsEntity.DATA2 + " ASC" );

			if (c != null) {
				ArrayList<Map.Entry<String,Integer>> result = new ArrayList<>();
				while (c.moveToNext()) {
					if(!c.isNull(0) && !c.isNull(1)){
						result.add(new AbstractMap.SimpleEntry<>(c.getString(0), c.getInt(1)));
						/*if(!MyApp.fids.containsKey(c.getString(0))){

						}*/

					}


				}
				c.close();
				return result;
			}

		}
		return null;
	}

	/*
		Used by sync adapter to remove contact
		time : new timestamp all contacts with an older timestamp are deleted

	 */
	static  ArrayList<String> RemoveContactByDate(Context co,Account acc,String time) throws RemoteException, OperationApplicationException{


		Uri rawContactUri = RawContactsEntity.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, acc.name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, acc.type)
				.build();
		Cursor c = co.getContentResolver().query(rawContactUri,new String[]{ RawContacts.SOURCE_ID}, RawContacts.SYNC3 + " <> '"+time+"' ", null,  null );
		ArrayList<String> res = new ArrayList<>();
		if (c != null) {
			int count = c.getCount();
			for(int i = 0; i< count;i++){
				c.moveToPosition(i);
				if(!res.contains(c.getString(0))){
					res.add(c.getString(0));
				}

			}
			c.close();
		}

		ArrayList<ContentProviderOperation> ops =
				new ArrayList<>();
		ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
				.withSelection(RawContacts.SYNC3 + " <> '"+time+"' AND "+RawContacts.ACCOUNT_TYPE +" = '"+acc.type+"' AND "+RawContacts.ACCOUNT_NAME +" = '"+acc.name+"'",null)
				.build());
		co.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		return res;


	}



}
