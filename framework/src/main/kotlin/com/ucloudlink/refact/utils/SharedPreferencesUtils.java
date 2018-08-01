package com.ucloudlink.refact.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesUtils {

	public static String PREFERENCE_NAME = "system_config";
	
	private static Context context;

	public static void init(Context context){
		SharedPreferencesUtils.context = context;
	}
	
	/**
	 * put string preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to modify
	 * @param value
	 *            The new value for the preference
	 * @return True if the new values were successfully written to persistent
	 *         storage.
	 */
	public static void putString(Context context, String key, String value) {
		putString(context,PREFERENCE_NAME,key,value);
	}


	public static void putString(String key, String value) {
		putString(context,PREFERENCE_NAME,key,value);
	}

	public static String getString(String key) {
		return getString(context, key, "");
	}
	
	public static void putString(Context context,String spName, String key, String value) {
		SharedPreferences settings = context.getSharedPreferences(
				spName, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(key, value);
		editor.apply();
	}

	/**
	 * get string preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @return The preference value if it exists, or null. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a string
	 * @see #getString(Context, String, String)
	 */
	public static String getString(Context context, String key) {
		return getString(context, key, "");
	}
	
	/**
	 * get string preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @param defaultValue
	 *            Value to return if this preference does not exist
	 * @return The preference value if it exists, or defValue. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a string
	 */
	public static String getString(Context context, String key,
								   String defaultValue) {
		return getString(context,PREFERENCE_NAME,key,defaultValue);
	}
	public static String getString(Context context, String spName,String key,
								   String defaultValue) {
		SharedPreferences settings = context.getSharedPreferences(
				spName, Context.MODE_PRIVATE);
		return settings.getString(key, defaultValue);
	}

	/**
	 * put int preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to modify
	 * @param value
	 *            The new value for the preference
	 * @return True if the new values were successfully written to persistent
	 *         storage.
	 */
	public static void putInt(Context context, String key, int value) {
		putInt(context,PREFERENCE_NAME,key,value);
	}
	public static void putInt(Context context,String spName,  String key, int value) {
		SharedPreferences settings = context.getSharedPreferences(
				spName, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt(key, value);
		editor.apply();
	}

	/**
	 * get int preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @return The preference value if it exists, or 0. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a int
	 * @see #getInt(Context, String, int)
	 */
	public static int getInt(Context context, String key) {
		return getInt(context, key, 0);
	}

	/**
	 * get int preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @param defaultValue
	 *            Value to return if this preference does not exist
	 * @return The preference value if it exists, or defValue. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a int
	 */
	public static int getInt(Context context, String key, int defaultValue) {
		return getInt(context,PREFERENCE_NAME,key, defaultValue);
	}
	public static int getInt(Context context,String spName, String key, int defaultValue) {
		SharedPreferences settings = context.getSharedPreferences(
				spName, Context.MODE_PRIVATE);
		return settings.getInt(key, defaultValue);
	}

	/**
	 * put long preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to modify
	 * @param value
	 *            The new value for the preference
	 * @return True if the new values were successfully written to persistent
	 *         storage.
	 */
	public static void putLong(Context context, String key, long value) {
		SharedPreferences settings = context.getSharedPreferences(
				PREFERENCE_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putLong(key, value);
		editor.apply();
	}

	/**
	 * get long preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @return The preference value if it exists, or 0. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a long
	 * @see #getLong(Context, String, long)
	 */
	public static long getLong(Context context, String key) {
		return getLong(context, key, 0);
	}

	/**
	 * get long preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @param defaultValue
	 *            Value to return if this preference does not exist
	 * @return The preference value if it exists, or defValue. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a long
	 */
	public static long getLong(Context context, String key, long defaultValue) {
		SharedPreferences settings = context.getSharedPreferences(
				PREFERENCE_NAME, Context.MODE_PRIVATE);
		return settings.getLong(key, defaultValue);
	}

	/**
	 * put float preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to modify
	 * @param value
	 *            The new value for the preference
	 * @return True if the new values were successfully written to persistent
	 *         storage.
	 */
	public static void putFloat(Context context, String key, float value) {
		SharedPreferences settings = context.getSharedPreferences(
				PREFERENCE_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putFloat(key, value);
		editor.apply();
	}

	/**
	 * get float preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @return The preference value if it exists, or 0. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a float
	 * @see #getFloat(Context, String, float)
	 */
	public static float getFloat(Context context, String key) {
		return getFloat(context, key, 0);
	}

	/**
	 * get float preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @param defaultValue
	 *            Value to return if this preference does not exist
	 * @return The preference value if it exists, or defValue. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a float
	 */
	public static float getFloat(Context context, String key, float defaultValue) {
		SharedPreferences settings = context.getSharedPreferences(
				PREFERENCE_NAME, Context.MODE_PRIVATE);
		return settings.getFloat(key, defaultValue);
	}
	/**
	 * put boolean preferences
	 *
	 * @param key
	 *            The name of the preference to modify
	 * @param value
	 *            The new value for the preference
	 * @return True if the new values were successfully written to persistent
	 *         storage.
	 */
	public static void putBoolean( String key, boolean value) {
		if(null != context) {
			SharedPreferences settings = context.getSharedPreferences(
					PREFERENCE_NAME, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(key, value);
			editor.apply();
		}else {
			throw new RuntimeException("init SharedPreferencesUtils please");
		}
	}
	/**
	 * put boolean preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to modify
	 * @param value
	 *            The new value for the preference
	 * @return True if the new values were successfully written to persistent
	 *         storage.
	 */
	public static void putBoolean(Context context, String key, boolean value) {
		if(null != context) {
			SharedPreferences settings = context.getSharedPreferences(
					PREFERENCE_NAME, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(key, value);
			editor.apply();
		}
	}

	public static void putBoolean(Context context, String spName, String key, boolean value) {
		if(null != context) {
			SharedPreferences settings = context.getSharedPreferences(
					spName, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(key, value);
			editor.apply();
		}
	}

	/**
	 * get boolean preferences, default is false
	 *
	 * @param key
	 *            The name of the preference to retrieve
	 * @return The preference value if it exists, or false. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a boolean
	 * @see #getBoolean(Context, String, boolean)
	 */
	public static boolean getBoolean(String key) {
		return getBoolean(key, false);
	}
	/**
	 * get boolean preferences, default is false
	 *
	 * @param key
	 *            The name of the preference to retrieve
	 * @return The preference value if it exists, or false. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a boolean
	 * @see #getBoolean(Context, String, boolean)
	 */
	public static boolean getBoolean(String key,boolean defaultValue) {
		if (context==null) {
			throw new RuntimeException("init SharedPreferencesUtils please");
		}
		return getBoolean(context, key, defaultValue);
	}
	
	/**
	 * get boolean preferences, default is false
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @return The preference value if it exists, or false. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a boolean
	 * @see #getBoolean(Context, String, boolean)
	 */
	public static boolean getBoolean(Context context, String key) {
		return getBoolean(context, key, false);
	}

	/**
	 * get boolean preferences
	 * 
	 * @param context
	 * @param key
	 *            The name of the preference to retrieve
	 * @param defaultValue
	 *            Value to return if this preference does not exist
	 * @return The preference value if it exists, or defValue. Throws
	 *         ClassCastException if there is a preference with this name that
	 *         is not a boolean
	 */
	public static boolean getBoolean(Context context, String key,
									 boolean defaultValue) {
		SharedPreferences settings = context.getSharedPreferences(
				PREFERENCE_NAME, Context.MODE_PRIVATE);
		return settings.getBoolean(key, defaultValue);
	}

	public static boolean getBoolean(Context context, String spName, String key,
									 boolean defaultValue) {
		SharedPreferences settings = context.getSharedPreferences(
				spName, Context.MODE_PRIVATE);
		return settings.getBoolean(key, defaultValue);
	}

	public static boolean isContain(Context context, String preferenceName){
			boolean isContain = false;
			SharedPreferences settings = context.getSharedPreferences(
					PREFERENCE_NAME, Context.MODE_PRIVATE);
			isContain  =  settings.contains(preferenceName);

			return isContain;
	}

	public static boolean isContain(Context context, String spName, String preferenceName){
		boolean isContain = false;
		SharedPreferences settings = context.getSharedPreferences(
				spName, Context.MODE_PRIVATE);
		isContain  =  settings.contains(preferenceName);

		return isContain;
	}
}
