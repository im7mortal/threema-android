package ch.threema.app.fragments.wizard;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.slf4j.Logger;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import ch.threema.android.LifecycleAwareAsyncTask;
import ch.threema.app.R;
import ch.threema.app.activities.wizard.WizardBaseActivity;
import ch.threema.android.textwatchers.SimpleTextWatcher;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.RuntimeUtil;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.common.JavaCompat.isNullOrEmpty;

/**
 * Example:
 * countryName: Switzerland
 * region:      CH
 * isoCode:     CH
 * countryCode: 41
 * prefix:      +41
 */

public class WizardFragment3 extends WizardFragment {
    private static final Logger logger = getThreemaLogger("WizardFragment3");

    private EditText phonePrefixEditText, phoneEditText, emailEditText;
    private CountryListAdapter countryListAdapter;
    private Spinner countrySpinner;
    private AsYouTypeFormatter phoneNumberFormatter;
    private LifecycleAwareAsyncTask<Void, ArrayList<Map<String, String>>> countryListTask;
    public static final int PAGE_ID = 3;

    @Override
    public View onCreateView(
        LayoutInflater inflater,
        final ViewGroup container,
        Bundle savedInstanceState
    ) {
        View rootView = Objects.requireNonNull(super.onCreateView(inflater, container, savedInstanceState));

        TextView title = rootView.findViewById(R.id.wizard_title);
        title.setText(R.string.new_wizard_help_your_friends_find_you);

        // inflate content layout
        contentViewStub.setLayoutResource(R.layout.fragment_wizard3);
        contentViewStub.inflate();

        WizardFragment4.SettingsInterface callback = (WizardFragment4.SettingsInterface) requireActivity();

        countrySpinner = rootView.findViewById(R.id.country_spinner);

        final LinearLayout phoneInputContainer = rootView.findViewById(R.id.phone_input_container);

        final TextInputLayout phonePrefixEditTextLayout = rootView.findViewById(R.id.wizard_prefix_layout);
        final TextInputLayout phoneEditTextLayout = rootView.findViewById(R.id.wizard_phone_layout);
        phonePrefixEditText = rootView.findViewById(R.id.wizard_prefix);
        phonePrefixEditText.setText("+");
        phoneEditText = rootView.findViewById(R.id.wizard_phone);

        final TextInputLayout emailEditTextLayout = rootView.findViewById(R.id.wizard_email_layout);
        emailEditText = rootView.findViewById(R.id.wizard_email);

        if (!ConfigUtils.isWorkBuild()) {
            emailEditTextLayout.setVisibility(View.GONE);
            ((TextView) rootView.findViewById(R.id.scooter)).setText(getString(R.string.new_wizard_link_mobile_only));
        }

        if (callback.isReadOnlyProfile()) {
            emailEditTextLayout.setEnabled(false);
            phonePrefixEditTextLayout.setEnabled(false);
            phoneEditTextLayout.setEnabled(false);
            countrySpinner.setEnabled(false);
            rootView.findViewById(R.id.disabled_by_policy).setVisibility(View.VISIBLE);
        } else {
            emailEditText.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(@NonNull Editable editable) {
                    if (getActivity() != null) {
                        ((OnSettingsChangedListener) getActivity()).onEmailSet(editable.toString());
                    }
                }
            });

            phonePrefixEditText.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(@NonNull Editable editable) {
                    String prefixString = editable.toString();
                    if (!prefixString.startsWith("+")) {
                        phonePrefixEditText.setText("+");
                        Selection.setSelection(phonePrefixEditText.getText(), phonePrefixEditText.getText().length());
                    } else if (prefixString.length() > 1 && countryListAdapter != null) {
                        try {
                            int countryCode = Integer.parseInt(prefixString.substring(1));
                            String region = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
                            int position = countryListAdapter.getPosition(region);

                            if (position > -1) {
                                countrySpinner.setSelection(position);
                                setPhoneNumberFormatter(countryCode);
                                ((OnSettingsChangedListener) requireActivity()).onPrefixSet(phonePrefixEditText.getText().toString());
                            }
                        } catch (NumberFormatException e) {
                            logger.error("Exception", e);
                        }
                    }
                }
            });

            phoneEditText.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(@NonNull Editable editable) {
                    if (!TextUtils.isEmpty(editable) && phoneNumberFormatter != null) {
                        phoneNumberFormatter.clear();

                        String number = editable.toString().replaceAll("[^\\d.]", "");
                        String formattedNumber = null;

                        for (int i = 0; i < number.length(); i++) {
                            formattedNumber = phoneNumberFormatter.inputDigit(number.charAt(i));
                        }

                        if (formattedNumber != null && !editable.toString().equals(formattedNumber)) {
                            editable.replace(0, editable.length(), formattedNumber);
                        }
                    }
                    Activity activity = getActivity();
                    if (activity != null) {
                        ((OnSettingsChangedListener) activity).onPhoneSet(editable.toString());
                    }
                }
            });

            if (!ConfigUtils.isWorkBuild()) {
                this.phoneEditText.setImeOptions(EditorInfo.IME_ACTION_GO);
                this.phoneEditText.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                        if (getActivity() != null && isAdded()) {
                            ((WizardBaseActivity) getActivity()).nextPage();
                        }
                        return true;
                    }
                    return false;
                });
            }
        }

        TextView presetEmailText = rootView.findViewById(R.id.preset_email_text);
        TextView presetPhoneText = rootView.findViewById(R.id.preset_phone_text);

        if (!isNullOrEmpty(callback.getPresetEmail())) {
            emailEditTextLayout.setVisibility(View.GONE);
            presetEmailText.setVisibility(View.VISIBLE);
        }

        if (!isNullOrEmpty(callback.getPresetPhone())) {
            phoneInputContainer.setVisibility(View.GONE);
            countrySpinner.setVisibility(View.GONE);
            presetPhoneText.setVisibility(View.VISIBLE);
        } else {
            // load country list
            countryListTask = new LifecycleAwareAsyncTask<>() {
                final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

                @Override
                protected ArrayList<Map<String, String>> doInBackground(Void params) {
                    Set<String> regions = phoneNumberUtil.getSupportedRegions();
                    ArrayList<Map<String, String>> results = new ArrayList<>(regions.size());
                    for (String region : regions) {
                        Map<String, String> data = new HashMap<>(2);
                        data.put("name", getCountryName(region));
                        data.put("prefix", "+" + PhoneNumberUtil.getInstance().getCountryCodeForRegion(region));
                        results.add(data);
                    }
                    Collections.sort(results, new CountryNameComparator());


                    Map<String, String> data = new HashMap<>(2);
                    data.put("name", getString(R.string.new_wizard_select_country));
                    data.put("prefix", "");
                    results.add(data);

                    return results;
                }

                @Override
                protected void onPostExecute(final ArrayList<Map<String, String>> result) {
                    countryListAdapter = new CountryListAdapter(android.R.layout.simple_spinner_dropdown_item, result);
                    countrySpinner.setAdapter(countryListAdapter);
                    countrySpinner.setSelection(countryListAdapter.getCount());
                    countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position < result.size() - 1) {
                                String prefixString = result.get(position).get("prefix");
                                phonePrefixEditText.setText(prefixString);

                                if (!isNullOrEmpty(prefixString) && prefixString.length() > 1) {
                                    setPhoneNumberFormatter(Integer.parseInt(prefixString.substring(1)));
                                }
                                phoneEditText.requestFocus();
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                            phonePrefixEditText.setText("+");
                        }
                    });

                    if (phonePrefixEditText.getText().length() <= 1) {
                        String countryCode = localeService.getCountryCodePhonePrefix();
                        if (!isNullOrEmpty(countryCode)) {
                            phonePrefixEditText.setText(countryCode);
                            ((OnSettingsChangedListener) getActivity()).onPrefixSet(phonePrefixEditText.getText().toString());
                            phoneEditText.requestFocus();
                        }
                    }
                }
            };
            countryListTask.execute(WizardFragment3.this, null);
        }

        return rootView;
    }

    @Override
    protected int getAdditionalInfoText() {
        return ConfigUtils.isWorkBuild() ? R.string.new_wizard_info_link : R.string.new_wizard_info_link_phone_only;
    }

    private void showEditTextError(@NonNull EditText editText, boolean show) {
        editText.setCompoundDrawablesWithIntrinsicBounds(
            0,
            0,
            show
                ? R.drawable.ic_error_red_24dp
                : 0,
            0
        );
    }

    private String getCountryName(String region) {
        if (!isNullOrEmpty(region)) {
            return new Locale("", region).getDisplayCountry(Locale.getDefault());
        } else {
            return "";
        }
    }

    void setPhoneNumberFormatter(int countryCode) {
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        String regionCode = phoneNumberUtil.getRegionCodeForCountryCode(countryCode);

        if (!isNullOrEmpty(regionCode)) {
            this.phoneNumberFormatter = phoneNumberUtil.getAsYouTypeFormatter(regionCode);
        } else {
            this.phoneNumberFormatter = null;
        }
    }

    private static class CountryNameComparator implements Comparator<Map<String, String>> {
        @Override
        public int compare(Map<String, String> lhs, Map<String, String> rhs) {
            // Compare two strings in the default locale
            Collator collator = Collator.getInstance();
            return collator.compare(lhs.get("name"), rhs.get("name"));
        }
    }

    private class CountryListAdapter extends BaseAdapter implements SpinnerAdapter {
        private final List<Map<String, String>> list;
        private final LayoutInflater inflater;
        private final int resource;

        public CountryListAdapter(int resource, List<Map<String, String>> objects) {
            this.inflater = requireActivity().getLayoutInflater();
            this.list = objects;
            this.resource = resource;
        }

        private class ViewHolder {
            TextView country;
        }

        @Override
        public int getCount() {
            int count = list.size();
            return count > 0 ? count - 1 : count;
        }

        @Override
        public Map<String, String> getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = inflater.inflate(this.resource, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.country = convertView.findViewById(android.R.id.text1);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            Map<String, String> map = list.get(position);
            viewHolder.country.setText(map.get("name"));

            return convertView;
        }

        public int getPosition(String region) {
            String countryName = getCountryName(region);
            for (int i = 0; i < list.size(); i++) {
                Map<String, String> map = list.get(i);
                String name = map.get("name");
                if (name != null && name.equalsIgnoreCase(countryName)) {
                    return i;
                }
            }
            return -1;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // make sure asynctask is cancelled before detaching fragment
        if (countryListTask != null) {
            countryListTask.cancel();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        new Handler(Looper.getMainLooper()).postDelayed(() -> RuntimeUtil.runOnUiThread(() -> {
            initValues();
            if (phoneEditText != null) {
                phoneEditText.requestFocus();
                EditTextUtil.showSoftKeyboard(phoneEditText);
            }
        }), 50);
    }

    @Override
    public void onPause() {
        if (this.phoneEditText != null) {
            this.phoneEditText.clearFocus();
            EditTextUtil.hideSoftKeyboard(this.phoneEditText);
        }
        super.onPause();
    }

    void initValues() {
        if (isResumed()) {
            WizardFragment4.SettingsInterface callback = (WizardFragment4.SettingsInterface) requireActivity();
            emailEditText.setText(callback.getEmail());

            if (isNullOrEmpty(callback.getPresetEmail())) {
                showEditTextError(emailEditText, !isNullOrEmpty(callback.getEmail()) && !Patterns.EMAIL_ADDRESS.matcher(callback.getEmail()).matches());
            }

            phonePrefixEditText.setText(callback.getPrefix());
            phoneEditText.setText(callback.getNumber());
            if (isNullOrEmpty(callback.getPresetPhone())) {
                showEditTextError(phoneEditText, !isNullOrEmpty(callback.getNumber()) && isNullOrEmpty(callback.getPhone()));
            }
        }
    }

    public interface OnSettingsChangedListener {
        void onPrefixSet(String prefix);

        void onPhoneSet(String phoneNumber);

        void onEmailSet(String email);
    }
}
