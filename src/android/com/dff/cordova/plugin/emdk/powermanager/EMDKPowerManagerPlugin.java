package com.dff.cordova.plugin.emdk.powermanager;

import java.io.StringReader;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKManager.EMDKListener;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.ProfileManager;

/**
 * This plugin implements an interface to the PowerManager Android API.
 *
 * @author dff solutions
 */
public class EMDKPowerManagerPlugin extends CordovaPlugin implements EMDKListener {
	private Context appContext;

	// Assign the profile name used in EMDKConfig.xml
	private String profileName = "PowerManagerProfile";

	// Declare a variable to store ProfileManager object
	private ProfileManager profileManager = null;

	// Declare a variable to store EMDKManager object
	private EMDKManager emdkManager = null;

	public static final int OPTION_DO_NOTHING        = 0;
	public static final int OPTION_SLEEP_MODE        = 1;
	public static final int OPTION_REBOOT            = 4;
	public static final int OPTION_ENTERPRISE_RESET  = 5;
	public static final int OPTION_FACTORY_RESET     = 6;
	public static final int OPTION_FULL_DDEVICE_WIPE = 7;
	public static final int OPTION_OS_UPDATE         = 8;
	
	// Initial Value of the Power Manager options to be executed in the
	// onOpened() method when the EMDK is ready. Default Value set in the wizard
	// is 0.
	// 0 -> Do Nothing
	// 1 -> Sleep Mode
	// 4 -> Reboot
	// 5 -> Enterprise Reset
	// 6 -> Factory Reset
	// 7 -> Full Device Wipe
	// 8 -> OS Update
	private int value = OPTION_DO_NOTHING;

	// Contains the parm-error name (sub-feature that has error)
	private String errorName = "";

	// Contains the characteristic-error type (Root feature that has error)
	private String errorType = "";

	// contains the error description for parm or characteristic error.
	private String errorDescription = "";
	
	/**
	 * Called after plugin construction and fields have been initialized. Prefer
	 * to use pluginInitialize instead since there is no value in having
	 * parameters on the initialize() function.
	 * 
	 * @param cordova
	 * @param webView
	 */
	public void pluginInitialize() {
		LOG.i(this.getClass().getName(), "Initialize PowerManager");

		super.pluginInitialize();
		this.appContext = this.cordova.getActivity().getApplicationContext();

		try {
			// The EMDKManager object will be created and returned in the callback.
			EMDKResults results = EMDKManager.getEMDKManager(appContext, this);
			LOG.d(this.getClass().getName(), "EMDKResult", EMDKResultToJson(results));
			
			// Check the return status of getEMDKManager
			if (results.statusCode == EMDKResults.STATUS_CODE.SUCCESS) {
				LOG.i(this.getClass().getName(), "EMDKManager object creation success");
			}
			else {
				LOG.e(this.getClass().getName(), "EMDKManager object creation failed");
			}
		}
		catch (Exception e) {
			LOG.e(this.getClass().getName(), e.getMessage(), e);
		}
	}

	@Override
	public void onClosed() {
		emdkManager.release();

	}

	public void onDestroy() {
		super.onDestroy();
		// Clean up the objects created by EMDK manager
		emdkManager.release();
	}

	@Override
	public void onOpened(EMDKManager emdkManager) {
		this.emdkManager = emdkManager;

		// Get the ProfileManager object to process the profiles
		profileManager = (ProfileManager) emdkManager
				.getInstance(EMDKManager.FEATURE_TYPE.PROFILE);

		if (profileManager != null) {
			String[] modifyData = new String[1];

			// Call processProfile with profile name and SET flag to create the
			// profile. The modifyData can be null.
			EMDKResults results = profileManager.processProfile(profileName,
					ProfileManager.PROFILE_FLAG.SET, modifyData);

			try {
				Log.i(this.getClass().getName(), "EMDKResult: " + EMDKResultToJson(results));
			}
			catch (JSONException e) {
				e.printStackTrace();
			}
			
			if (results.statusCode == EMDKResults.STATUS_CODE.CHECK_XML) {
				// Method call to handle EMDKResult
				handleEMDKResult(results);
			} else {
				LOG.e(this.getClass().getName(), "Failed to apply profile... "
						+ profileName);
			}
		}
	}
	
	private JSONObject EMDKResultToJson(EMDKResults results) throws JSONException {
		JSONObject jsonResult = new JSONObject();
		String status = "";
		
		switch (results.statusCode) {
		case SUCCESS:
            status = "SUCCESS";
            break;
        case FAILURE:
            status = "FAILURE";
            break;
        case CHECK_XML:
            status = "CHECK_XML";
            break;
        case EMDK_NOT_OPENED:
            status = "EMDK_NOT_OPENED";
            break;
        case EMPTY_PROFILENAME:
            status = "EMPTY_PROFILENAME";
            break;
        case NO_DATA_LISTENER:
            status = "NO_DATA_LISTENER";
            break;
        case NULL_POINTER:
            status = "NULL_POINTER";
            break;
        case PREVIOUS_REQUEST_IN_PROGRESS:
            status = "PREVIOUS_REQUEST_IN_PROGRESS";
            break;
        case PROCESSING:
            status = "PROCESSING";
            break;
        case UNKNOWN:
            status = "UNKNOWN";
            break;
        default:
            status = "UNKNOWN";
            break;
		}
		
		jsonResult.put("status", status);
		jsonResult.put("statusCode", results.statusCode);		
		jsonResult.put("statusString", results.getStatusString());
		jsonResult.put("statusDocument", results.getStatusDocument());
		jsonResult.put("extendedStatusMessage", results.getExtendedStatusMessage());
		jsonResult.put("successFeaturesCount", results.getSuccessFeaturesCount());
		jsonResult.put("totalFeaturesCount", results.getTotalFeaturesCount());
		
		return jsonResult;
	}

	// Method to handle EMDKResult by extracting response and parsing it
	public void handleEMDKResult(EMDKResults results) {
		// Get XML response as a String
		String statusXMLResponse = results.getStatusString();

		try {
			// Create instance of XML Pull Parser to parse the response
			XmlPullParser parser = Xml.newPullParser();
			// Provide the string response to the String Reader that reads
			// for the parser
			parser.setInput(new StringReader(statusXMLResponse));
			// Call method to parse the response
			parseXML(parser);
		} catch (XmlPullParserException e) {
			e.printStackTrace();
			LOG.e(this.getClass().getName(), e.toString());
		}

		// Method call to display results in a dialog
		LOG.e(this.getClass().getName(), "Name: " + errorName + "; Type: "
				+ errorType + "; desc: " + errorDescription);
	}

	// Method to parse the XML response using XML Pull Parser
	public void parseXML(XmlPullParser myParser) {
		int event;
		try {
			event = myParser.getEventType();
			while (event != XmlPullParser.END_DOCUMENT) {
				String name = myParser.getName();
				switch (event) {
				case XmlPullParser.START_TAG:
					// Get Status, error name and description in case of
					// parm-error
					if (name.equals("parm-error")) {
						errorName = myParser.getAttributeValue(null, "name");
						errorDescription = myParser.getAttributeValue(null,
								"desc");

						// Get Status, error type and description in case of
						// parm-error
					} else if (name.equals("characteristic-error")) {
						errorType = myParser.getAttributeValue(null, "type");
						errorDescription = myParser.getAttributeValue(null,
								"desc");
					}
					break;
				case XmlPullParser.END_TAG:
					break;
				}
				event = myParser.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Method that applies the modified settings to the EMDK Profile based on
	// user selected options of Power Manager feature.
	private void modifyProfile_XMLString(CallbackContext callbackContext) throws JSONException {
		if (profileManager != null) {
			// Prepare XML to modify the existing profile
			String[] modifyData = new String[1];
			// Modified XML input for Sleep and Reboot feature based on user
			// selected options of radio button
			// value = 1 -> Sleep Mode
			// value = 4 -> Reboot
			modifyData[0] = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
					+ "<characteristic type=\"Profile\">"
					+ 	"<parm name=\"ProfileName\" value=\"PowerManagerProfile\"/>"
					+ 	"<characteristic type=\"PowerMgr\">"
					+ 		"<parm name=\"ResetAction\" value=\"" + value + "\"/>"
					+ 	"</characteristic>"
					+ "</characteristic>";
	
			// Call process profile to modify the profile of specified profile
			// name
			EMDKResults results = profileManager.processProfile(profileName,
					ProfileManager.PROFILE_FLAG.SET, modifyData);			

			JSONObject jsonResult = EMDKResultToJson(results);
			
			Log.i(this.getClass().getName(), "EMDKResult: " + EMDKResultToJson(results));
	
			if (results.statusCode == EMDKResults.STATUS_CODE.CHECK_XML) {
				// Method call to handle EMDKResult
				handleEMDKResult(results);
			}
			else {
				LOG.e(this.getClass().getName(), "Failed to apply profile... "
						+ profileName);
			}
			
			callbackContext.success(jsonResult);
		}
		else {
			String msg = "profile manager not instantiated";
			LOG.e(this.getClass().getName(), msg);
			callbackContext.error(msg);
		}
	}

	/**
	 * Executes the request.
	 *
	 * This method is called from the WebView thread. To do a non-trivial amount
	 * of work, use: cordova.getThreadPool().execute(runnable);
	 *
	 * To run on the UI thread, use:
	 * cordova.getActivity().runOnUiThread(runnable);
	 *
	 * @param action           The action to execute.
	 * @param args             The exec() arguments.
	 * @param callbackContext  The callback context used when calling back into JavaScript.
	 * @return Whether the action was valid.
	 */
	@Override
	public boolean execute(String action, JSONArray args,
			final CallbackContext callbackContext) throws JSONException {

		LOG.i(this.getClass().getName(), "call for action: " + action
				+ "; parms: " + args);

		if (action.equals("reboot")) {
			// String reason = jsonArgs.getString(reasonArg);
			// this.powerManager.reboot(reason);
			value = OPTION_REBOOT; // 4 - Perform Reset/Reboot (Reboot Device)
			// Apply Settings selected by user
			modifyProfile_XMLString(callbackContext);

			return true;
		}

		return false;
	}
}
