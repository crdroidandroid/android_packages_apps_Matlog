package com.pluscubed.logcat.helper;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;

import com.pluscubed.logcat.data.FilterQueryWithLevel;
import com.pluscubed.logcat.data.SortedFilterArrayAdapter;
import com.pluscubed.logcat.util.ArrayUtil;
import com.pluscubed.logcat.util.Callback;

import org.omnirom.logcat.R;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class DialogHelper {

    public interface InputTextCallback {
        void setInputText(String text);
    }

    public static void startRecordingWithProgressDialog(final String filename,
                                                        final String filterQuery, final String logLevel, final Runnable onPostExecute, final Context context) {

        final ProgressDialog progressDialog = ProgressDialog.show(context,
                context.getResources().getString(R.string.dialog_please_wait),
                context.getResources().getString(R.string.dialog_initializing_recorder),
                true, false);


        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                ServiceHelper.startBackgroundServiceIfNotAlreadyRunning(context, filename, filterQuery, logLevel);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        if (onPostExecute != null) {
                            onPostExecute.run();
                        }
                    }
                });
            }
        }).start();

    }

    public static boolean isInvalidFilename(CharSequence filename) {

        String filenameAsString;

        return TextUtils.isEmpty(filename)
                || (filenameAsString = filename.toString()).contains("/")
                || filenameAsString.contains(":")
                || filenameAsString.contains(" ")
                || !filenameAsString.endsWith(".txt");

    }

    public static void showFilterDialogForRecording(final Context context, final String queryFilterText,
                                                    final String logLevelText, final List<String> filterQuerySuggestions,
                                                    final Callback<FilterQueryWithLevel> callback) {

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams") View filterView = inflater.inflate(R.layout.dialog_recording_filter, null, false);

        // add suggestions to autocompletetextview
        final AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView) filterView.findViewById(R.id.filter_text);
        autoCompleteTextView.setText(queryFilterText);

        SortedFilterArrayAdapter<String> suggestionAdapter = new SortedFilterArrayAdapter<String>(
                context, R.layout.list_item_dropdown, filterQuerySuggestions);
        autoCompleteTextView.setAdapter(suggestionAdapter);

        // set values on spinner to be the log levels
        final Spinner spinner = (Spinner) filterView.findViewById(R.id.spinner);

        // put the word "default" after whatever the default log level is
        CharSequence[] logLevels = context.getResources().getStringArray(R.array.log_levels);
        String defaultLogLevel = Character.toString(PreferenceHelper.getDefaultLogLevelPreference(context));
        int index = ArrayUtil.indexOf(context.getResources().getStringArray(R.array.log_levels_values), defaultLogLevel);
        logLevels[index] = logLevels[index].toString() + " " + context.getString(R.string.default_in_parens);

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, logLevels);
        adapter.setDropDownViewResource(R.layout.list_item_dropdown);
        spinner.setAdapter(adapter);

        // in case the user has changed it, choose the pre-selected log level
        spinner.setSelection(ArrayUtil.indexOf(context.getResources().getStringArray(R.array.log_levels_values),
                logLevelText));

        // create alertdialog for the "Filter..." button
        new AlertDialog.Builder(context)
                .setTitle(R.string.title_filter)
                .setView(filterView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int logLevelIdx = spinner.getSelectedItemPosition();
                        String[] logLevelValues = context.getResources().getStringArray(R.array.log_levels_values);
                        String logLevelValue = logLevelValues[logLevelIdx];

                        String filterQuery = autoCompleteTextView.getText().toString();

                        callback.onCallback(new FilterQueryWithLevel(filterQuery, logLevelValue));
                    }
                })
                .show();
    }

    public static void stopRecordingLog(Context context) {
        ServiceHelper.stopBackgroundServiceIfRunning(context);
    }


    public static void showFilenameSuggestingDialog(final Context context,
                                                    final Runnable negativeCallback, final InputTextCallback inputCallback, int titleResId) {

        final View v = initFilenameInputDialog(context);
        final EditText input = v.findViewById(R.id.edit_text);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titleResId)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (negativeCallback != null) {
                            negativeCallback.run();
                        }
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText().toString();
                        inputCallback.setInputText(text);
                    }
                })
                .setMessage(R.string.enter_filename)
                .setView(v);

        AlertDialog show = builder.show();
    }

    public static View initFilenameInputDialog(final Context context) {
        View v = LayoutInflater.from(context).inflate(R.layout.dialog_input_text, null, false);
        final EditText editText = v.findViewById(R.id.edit_text);
        editText.setSingleLine();
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        // create an initial filename to suggest to the user
        String filename = createLogFilename();
        editText.setText(filename);

        // highlight everything but the .txt at the end
        editText.setSelection(0, filename.length() - 4);
        return v;
    }

    public static String createLogFilename() {
        Date date = new Date();
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(date);

        DecimalFormat twoDigitDecimalFormat = new DecimalFormat("00");
        DecimalFormat fourDigitDecimalFormat = new DecimalFormat("0000");

        String year = fourDigitDecimalFormat.format(calendar.get(Calendar.YEAR));
        String month = twoDigitDecimalFormat.format(calendar.get(Calendar.MONTH) + 1);
        String day = twoDigitDecimalFormat.format(calendar.get(Calendar.DAY_OF_MONTH));
        String hour = twoDigitDecimalFormat.format(calendar.get(Calendar.HOUR_OF_DAY));
        String minute = twoDigitDecimalFormat.format(calendar.get(Calendar.MINUTE));
        String second = twoDigitDecimalFormat.format(calendar.get(Calendar.SECOND));

        return year + "-" + month + "-" + day + "-" + hour + "-" + minute + "-" + second + ".txt";
    }

}
