package ch.threema.app.activities.poll;

import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.android.textwatchers.SimpleTextWatcher;
import ch.threema.app.utils.ViewUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.poll.PollModel;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class PollWizardFragment0 extends PollWizardFragment implements PollWizardActivity.PollWizardCallback {
    private static final Logger logger = getThreemaLogger("PollWizardFragment0");

    private EditText editText;
    private TextInputLayout textInputLayout;
    private CheckBox secretCheckbox;
    private CheckBox typeCheckbox;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        ViewGroup rootView = (ViewGroup) inflater.inflate(
            R.layout.fragment_poll_wizard0, container, false);

        this.editText = rootView.findViewById(R.id.wizard_edittext);
        this.editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == getResources().getInteger(R.integer.ime_wizard_next) || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (getPollActivity() != null) {
                        getPollActivity().nextPage();
                    }
                }
                return false;
            }
        });
        this.editText.addTextChangedListener(new SimpleTextWatcher() {
            public void afterTextChanged(@NonNull Editable editable) {
                if (getPollActivity() != null) {
                    getPollActivity().setPollDescription(editText.getText().toString());
                }
                if (editable.length() > 0) {
                    textInputLayout.setError(null);
                }
            }
        });

        this.textInputLayout = rootView.findViewById(R.id.wizard_edittext_layout);

        this.typeCheckbox = rootView.findViewById(R.id.type);
        this.typeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (getPollActivity() != null) {
                    getPollActivity().setPollAssessment(
                        isChecked ? PollModel.Assessment.MULTIPLE_CHOICE : PollModel.Assessment.SINGLE_CHOICE
                    );
                }
            }
        });
        this.secretCheckbox = rootView.findViewById(R.id.visibility);
        this.secretCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (getPollActivity() != null) {
                    getPollActivity().setPollType(
                        isChecked ? PollModel.Type.INTERMEDIATE : PollModel.Type.RESULT_ON_CLOSE
                    );
                }
            }
        });

        this.updateView();
        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public void updateView() {
        if (getPollActivity() != null) {
            ViewUtil.showAndSet(this.editText,
                this.getPollActivity().getPollDescription());
        }
        if (this.getPollActivity() != null) {
            ViewUtil.showAndSet(this.typeCheckbox,
                this.getPollActivity().getPollAssessment() == PollModel.Assessment.MULTIPLE_CHOICE);

            ViewUtil.showAndSet(this.secretCheckbox,
                this.getPollActivity().getPollType() == PollModel.Type.INTERMEDIATE);
        }
    }

    @Override
    public void onMissingTitle() {
        this.textInputLayout.setError(getString(R.string.title_cannot_be_empty));
        this.editText.setFocusableInTouchMode(true);
        this.editText.setFocusable(true);
        this.editText.requestFocus();
    }

    @Override
    public void onPageSelected(int page) {
        if (page == 1) {
            this.editText.clearFocus();
            this.editText.setFocusableInTouchMode(false);
            this.editText.setFocusable(false);
        } else {
            this.editText.setFocusableInTouchMode(true);
            this.editText.setFocusable(true);
        }
    }
}
