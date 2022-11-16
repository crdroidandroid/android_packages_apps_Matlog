package com.pluscubed.logcat.ui;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.pluscubed.logcat.LogcatRecordingService;
import com.pluscubed.logcat.RecordingWidgetProvider;
import com.pluscubed.logcat.data.FilterQueryWithLevel;
import com.pluscubed.logcat.helper.DialogHelper;
import com.pluscubed.logcat.helper.PreferenceHelper;
import com.pluscubed.logcat.helper.ServiceHelper;
import com.pluscubed.logcat.helper.WidgetHelper;
import com.pluscubed.logcat.util.Callback;

import org.omnirom.logcat.R;

import java.util.Arrays;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

import static com.pluscubed.logcat.RecordingWidgetProvider.log;

public class RecordLogDialogActivity extends AppCompatActivity {

    public static final String EXTRA_QUERY_SUGGESTIONS = "suggestions";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null &&
                intent.getAction().equals(RecordingWidgetProvider.ACTION_RECORD_OR_STOP)) {
            log.i("onCreate(); intent is: %s", intent);
            WidgetHelper.updateWidgets(getApplicationContext());

            boolean alreadyRunning = ServiceHelper.checkIfServiceIsRunning(this, LogcatRecordingService.class);
            if (alreadyRunning) {
                DialogHelper.stopRecordingLog(this);
                finish();
            } else {
                showDialog();
            }
        } else {
            showDialog();
        }
    }

    private void showDialog() {
        final String[] suggestions = (getIntent() != null && getIntent().hasExtra(EXTRA_QUERY_SUGGESTIONS))
                ? getIntent().getStringArrayExtra(EXTRA_QUERY_SUGGESTIONS) : new String[]{};

        DialogFragment fragment = ShowRecordLogDialog.newInstance(suggestions);
        fragment.show(getFragmentManager(), "showRecordLogDialog");
    }

    public static class ShowRecordLogDialog extends DialogFragment {

        public static final String QUERY_SUGGESTIONS = "suggestions";

        public static ShowRecordLogDialog newInstance(String[] suggestions) {
            ShowRecordLogDialog dialog = new ShowRecordLogDialog();
            Bundle args = new Bundle();
            args.putStringArray(QUERY_SUGGESTIONS, suggestions);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            //noinspection ConstantConditions
            final List<String> suggestions = Arrays.asList(getArguments().getStringArray(QUERY_SUGGESTIONS));

            final View v = DialogHelper.initFilenameInputDialog(getActivity());
            final EditText input = v.findViewById(R.id.edit_text);

            String defaultLogLevel = Character.toString(PreferenceHelper.getDefaultLogLevelPreference(getActivity()));
            final StringBuilder queryFilterText = new StringBuilder();
            final StringBuilder logLevelText = new StringBuilder(defaultLogLevel);
            final Activity activity = getActivity();
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.record_log)
                    .setMessage(R.string.enter_filename)
                    .setView(v)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String inputText = input.getText().toString();
                            if (DialogHelper.isInvalidFilename(inputText)) {
                                Toast.makeText(getActivity(), R.string.enter_good_filename, Toast.LENGTH_SHORT).show();
                            } else {
                                String filename = inputText;
                                Runnable runnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        activity.finish();
                                    }
                                };
                                DialogHelper.startRecordingWithProgressDialog(filename,
                                        queryFilterText.toString(), logLevelText.toString(), runnable, getActivity());
                            }
                        }
                    })
                    .setNeutralButton(R.string.text_filter_ellipsis, null);

            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface d) {
                    Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                    neutral.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            DialogHelper.showFilterDialogForRecording(getActivity(), queryFilterText.toString(),
                                    logLevelText.toString(), suggestions,
                                    new Callback<FilterQueryWithLevel>() {
                                        @Override
                                        public void onCallback(FilterQueryWithLevel result) {
                                            queryFilterText.replace(0, queryFilterText.length(), result.getFilterQuery());
                                            logLevelText.replace(0, logLevelText.length(), result.getLogLevel());
                                        }
                                    });
                        }
                    });
                    Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    negative.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            activity.finish();
                        }
                    });
                }
            });
            return dialog;
        }
    }
}
