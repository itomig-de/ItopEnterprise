// Copyright (C) 2011-2013 ITOMIG GmbH
//
//   This file is part of iTopEnterprise.
//
//   iTopEnterprise is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation, either version 3 of the License, or
//   (at your option) any later version.
//
//   iTopEnterprise is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with iTopEnterprise. If not, see <http://www.gnu.org/licenses/>

//   Google-GSON: Copyright 2008-2011 Google Inc.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0

package de.itomig.itopenterprise;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;

import de.itomig.itopenterprise.cmdb.CMDBObject;
import de.itomig.itopenterprise.cmdb.ItopTicket;
import de.itomig.itopenterprise.cmdb.ItopTicketDeserializer;

import static de.itomig.itopenterprise.ItopConfig.TAG;
import static de.itomig.itopenterprise.ItopConfig.debug;

public class GetItopJSON {

    /**
     * request data from itop server in json format - must be called from AsyncTask
     *
     * @param operation core/get etc
     * @param itopClass CI class
     * @param key  itop SELECT expression
     * @param output_fields attribute fields which should be returned
     * @return String with json data from server
     */
    public static String postJsonToItopServer(String operation,
                                              String itopClass, String key, String output_fields) {
        AndroidHttpClient client = AndroidHttpClient.newInstance("Android");
        String result = "";

        try {
            HttpPost request = new HttpPost();
            String url = ItopConfig.getItopUrl();

            String req = url + "/webservices/rest.php?version=1.0";
            if (debug)
                Log.i(TAG, "json request=" + req);
            request.setURI(new URI(req));

            ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
            postParameters.add(ItopConfig.getItopUserNameValuePair());
            postParameters.add(ItopConfig.getItopPwdNameValuePair());

            JSONObject jsd = new JSONObject();
            jsd.put("operation", operation);
            jsd.put("class", itopClass);
            jsd.put("key", key);
            jsd.put("output_fields", output_fields);

            postParameters.add(new BasicNameValuePair("json_data", jsd
                    .toString()));
            UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(
                    postParameters);
            request.setEntity(formEntity);

            // request.addHeader(HTTP.CONTENT_TYPE, "application/json");
            HttpResponse response = client.execute(request);
            String status = response.getStatusLine().toString();
            if (debug)
                Log.i(TAG, "status: " + status);

            if (status.contains("200") && status.contains("OK")) {

                // request worked fine, retrieved some data
                InputStream instream = response.getEntity().getContent();
                result = convertStreamToString(instream);
                //Log.d(TAG, "result is: " + result);
            } else // some error in http response
            {
                Log.e(TAG, "Get data - http-ERROR: " + status);
                result = "SERVER_ERROR: http status " + status;
            }

        } catch (Exception e) {
            // Toast does not work in background task
            Log.e(TAG, "Get data -  " + e.toString());
            result = "SERVER_ERROR: " + e.toString();
        } finally {
            client.close(); // needs to be done for androidhttpclient
            if (debug)
                Log.i(TAG, "...finally.. get data finished");
        }
        return result;

    }

    private static String convertStreamToString(InputStream is) {
        /*
		 * To convert the InputStream to String we use the
		 * BufferedReader.readLine() method. We iterate until the BufferedReader
		 * return null which means there's no more data to read. Each line will
		 * appended to a StringBuilder and returned as String.
		 */
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    /**
     * convert a json string to an ArrayList of a CMDBObject Type
     *
     * @param json string
     * @param T Type of data
     * @param context application context, call with =null when calling from background task
     * @param <T> Type of data
     * @return ArrayList<T> with the results, returns empty list when error or no data.
     */
    public static <T> ArrayList<T> getArrayFromJson(String json, Type T, Context context) {
        ArrayList<T> list = new ArrayList<T>();

        // check for server Error
        if ((json.length() >= 12) && json.substring(0,12).contains("SERVER_ERROR")) {
            if (context != null) {
                Toast.makeText(context, json, Toast.LENGTH_LONG).show();
            } else {
                Log.e(TAG,json);
            }
            return list;
        }
        String code = "100"; // json error code, "0" => everything is o.k.
        Log.d(TAG, "getArrayFromJson - Type=" + T.toString());

        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(json);
            code = jsonObject.getString("code");
            Log.d(TAG, "code=" + code);
            Log.d(TAG, "message=" + jsonObject.getString("message"));

        } catch (JSONException e) {
            Log.e(TAG, "error in getArrayFromJSON " + e.getMessage());
            Toast.makeText(context, "error in getArrayFromJSON " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if ((jsonObject != null) && (code.trim().equals("0"))) {
            try {

                JSONObject objects = jsonObject.getJSONObject("objects");
                Iterator<?> keys = objects.keys();

                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    Log.d(TAG, "key=" + key);
                    if (objects.get(key) instanceof JSONObject) {
                        // Log.d(TAG,"obj="+objects.get(key).toString());
                        JSONObject o = (JSONObject) objects.get(key);
                        int id = o.getInt("key");
                        Log.d(TAG,"id="+id);
                        JSONObject fields = o.getJSONObject("fields");
                        //Log.d(TAG, "fields=" + fields.toString());
                        final GsonBuilder gsonBuilder = new GsonBuilder();
                        gsonBuilder.registerTypeAdapter(ItopTicket.class, new ItopTicketDeserializer());
                        final Gson gson = gsonBuilder.create();

                        final T jf = gson.fromJson(fields.toString(), T);
                        if (jf instanceof CMDBObject) {
                            ((CMDBObject) jf).id = id;
                        }
                        list.add(jf);

                    }
                }
                Log.d(TAG, "code=" + jsonObject.getString("code"));
                Log.d(TAG, "message=" + jsonObject.getString("message"));

            } catch (JSONException e) {
                Log.e(TAG,"objects = null in JSON response.");
            }
        } // endif (jsonObject != null)

        return list;

    }

    public static String getMessage(String json) {
        String message = "";
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(json);
            int code = jsonObject.getInt("code");
            Log.d(TAG, "code=" + jsonObject.getString("code"));
            if (code != 0) { // if code!=0 there is an error
                message = jsonObject.getString("message");
                Log.d(TAG, "message=" + message);
            }

        } catch (JSONException e) {
            Log.e(TAG, "error in getArrayFromJSON " + e.getMessage());
            return e.getMessage();
        }

        return message;
    }
}
