package com.ugetdm.uget;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.ugetdm.uget.lib.Sequence;

public class SequenceForm implements UriBatchInterface {
    protected View view;
    protected Activity activity;
    private TextView preview;
    private Sequence sequence;
    public int rangeTypeEnableCountdown = 0;
    public String errorMessage;

    public static final class RangeType {
        public static final int none = 0;
        public static final int number = 1;
        public static final int character = 2;
    }

    public SequenceForm(Activity activity) {
        this.activity = activity;
        view = activity.getLayoutInflater().inflate(R.layout.form_sequence, null);

        preview = (TextView) view.findViewById(R.id.batch_seq_preview);
        // preview.setMovementMethod(new ScrollingMovementMethod());
        sequence = new Sequence();

        initRangeSpinner();
        initEditor();
    }

    public void initRangeSpinner() {
        SpinnerItems seqAdapter = new SpinnerItems(activity);
        seqAdapter.names = activity.getResources().getStringArray(R.array.batch_seq_type);
        seqAdapter.imageIds = new int[]{android.R.drawable.ic_menu_close_clear_cancel,
                R.drawable.ic_sort_09,
                R.drawable.ic_sort_az};

        Spinner spinner;
        spinner = (Spinner) view.findViewById(R.id.batch_seq_type1);
        spinner.setAdapter(seqAdapter);
        spinner.setSelection(RangeType.number);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                setRangeType(0, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        spinner = (Spinner) view.findViewById(R.id.batch_seq_type2);
        spinner.setAdapter(seqAdapter);
        spinner.setSelection(RangeType.none);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                setRangeType(1, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        spinner = (Spinner) view.findViewById(R.id.batch_seq_type3);
        spinner.setAdapter(seqAdapter);
        spinner.setSelection(RangeType.none);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                setRangeType(2, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    public void initEditor() {
        EditText editor;

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                showPreview();
            }
        };

        editor = (EditText) view.findViewById(R.id.batch_seq_uri_editor);
        editor.addTextChangedListener(textWatcher);

        editor = (EditText) view.findViewById(R.id.batch_seq_digits1);
        editor.addTextChangedListener(textWatcher);
        editor = (EditText) view.findViewById(R.id.batch_seq_from1);
        editor.addTextChangedListener(textWatcher);
        editor = (EditText) view.findViewById(R.id.batch_seq_to1);
        editor.addTextChangedListener(textWatcher);

        editor = (EditText) view.findViewById(R.id.batch_seq_digits2);
        editor.addTextChangedListener(textWatcher);
        editor = (EditText) view.findViewById(R.id.batch_seq_from2);
        editor.addTextChangedListener(textWatcher);
        editor = (EditText) view.findViewById(R.id.batch_seq_to2);
        editor.addTextChangedListener(textWatcher);

        editor = (EditText) view.findViewById(R.id.batch_seq_digits3);
        editor.addTextChangedListener(textWatcher);
        editor = (EditText) view.findViewById(R.id.batch_seq_from3);
        editor.addTextChangedListener(textWatcher);
        editor = (EditText) view.findViewById(R.id.batch_seq_to3);
        editor.addTextChangedListener(textWatcher);
    }

    // ------------------------------------------------------------------------
    // Range

    public void setRangeType(int nthRange, int type) {
        View digitsLabel, charCase;
        EditText digits, from, to;
        TextView toLabel;

        switch (nthRange) {
            default:
            case 0:
                charCase = view.findViewById(R.id.batch_seq_char_case1);
                digitsLabel = view.findViewById(R.id.batch_seq_digits1_label);
                digits = (EditText) view.findViewById(R.id.batch_seq_digits1);
                from = (EditText) view.findViewById(R.id.batch_seq_from1);
                to = (EditText) view.findViewById(R.id.batch_seq_to1);
                toLabel = (TextView) view.findViewById(R.id.batch_seq_to1_label);
                break;

            case 1:
                charCase = view.findViewById(R.id.batch_seq_char_case2);
                digitsLabel = view.findViewById(R.id.batch_seq_digits2_label);
                digits = (EditText) view.findViewById(R.id.batch_seq_digits2);
                from = (EditText) view.findViewById(R.id.batch_seq_from2);
                to = (EditText) view.findViewById(R.id.batch_seq_to2);
                toLabel = (TextView) view.findViewById(R.id.batch_seq_to2_label);
                break;

            case 2:
                charCase = view.findViewById(R.id.batch_seq_char_case3);
                digitsLabel = view.findViewById(R.id.batch_seq_digits3_label);
                digits = (EditText) view.findViewById(R.id.batch_seq_digits3);
                from = (EditText) view.findViewById(R.id.batch_seq_from3);
                to = (EditText) view.findViewById(R.id.batch_seq_to3);
                toLabel = (TextView) view.findViewById(R.id.batch_seq_to3_label);
                break;
        }

        switch (type) {
            case RangeType.none:
            default:
                digitsLabel.setVisibility(View.GONE);
                digits.setVisibility(View.GONE);
                charCase.setVisibility(View.INVISIBLE);
                from.setEnabled(false);
                to.setEnabled(false);
                toLabel.setEnabled(false);
                if (rangeTypeEnableCountdown > 0)
                    rangeTypeEnableCountdown--;
                break;

            case RangeType.number:
                digitsLabel.setVisibility(View.VISIBLE);
                digits.setVisibility(View.VISIBLE);
                charCase.setVisibility(View.GONE);
                from.setEnabled(true);
                from.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
                to.setEnabled(true);
                to.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
                toLabel.setEnabled(true);
                if (from.getInputType() != InputType.TYPE_CLASS_NUMBER || (from.length() == 0 && to.length() == 0)) {
                    from.setInputType(InputType.TYPE_CLASS_NUMBER);
                    to.setInputType(InputType.TYPE_CLASS_NUMBER);
                    if (rangeTypeEnableCountdown > 0)
                        rangeTypeEnableCountdown--;
                    else {
                        from.setText("0");
                        to.setText("9");
                    }
                }
                break;

            case RangeType.character:
                digitsLabel.setVisibility(View.GONE);
                digits.setVisibility(View.GONE);
                charCase.setVisibility(View.VISIBLE);
                from.setEnabled(true);
                from.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
                to.setEnabled(true);
                to.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
                toLabel.setEnabled(true);
                if (from.getInputType() != InputType.TYPE_CLASS_TEXT || (from.length() == 0 && to.length() == 0)) {
                    from.setInputType(InputType.TYPE_CLASS_TEXT);
                    to.setInputType(InputType.TYPE_CLASS_TEXT);
                    if (rangeTypeEnableCountdown > 0)
                        rangeTypeEnableCountdown--;
                    else {
                        from.setText("a");
                        to.setText("z");
                    }
                }
                break;
        }

        // show preview after type changed.
        showPreview();
    }

    public boolean addRange(int nthRange) {
        EditText fromEditor, toEditor, digitsEditor;
        Spinner typeSpinner;
        int from, to, digits, type;

        switch (nthRange) {
            default:
            case 0:
                typeSpinner = (Spinner) view.findViewById(R.id.batch_seq_type1);
                fromEditor = (EditText) view.findViewById(R.id.batch_seq_from1);
                toEditor = (EditText) view.findViewById(R.id.batch_seq_to1);
                digitsEditor = (EditText) view.findViewById(R.id.batch_seq_digits1);
                break;

            case 1:
                typeSpinner = (Spinner) view.findViewById(R.id.batch_seq_type2);
                fromEditor = (EditText) view.findViewById(R.id.batch_seq_from2);
                toEditor = (EditText) view.findViewById(R.id.batch_seq_to2);
                digitsEditor = (EditText) view.findViewById(R.id.batch_seq_digits2);
                break;

            case 2:
                typeSpinner = (Spinner) view.findViewById(R.id.batch_seq_type3);
                fromEditor = (EditText) view.findViewById(R.id.batch_seq_from3);
                toEditor = (EditText) view.findViewById(R.id.batch_seq_to3);
                digitsEditor = (EditText) view.findViewById(R.id.batch_seq_digits3);
                break;
        }

        if (fromEditor.length() == 0 || toEditor.length() == 0)
            return false;

        type = typeSpinner.getSelectedItemPosition();
        switch (type) {
            case RangeType.number:
                try {
                    from = Integer.parseInt(fromEditor.getText().toString());
                    to = Integer.parseInt(toEditor.getText().toString());
                    digits = Integer.parseInt(digitsEditor.getText().toString());
                } catch (Exception e) {
                    return false;
                }
                break;

            case RangeType.character:
                try {
                    from = fromEditor.getText().toString().charAt(0);
                    to = toEditor.getText().toString().charAt(0);
                    digits = 0;
                } catch (Exception e) {
                    return false;
                }
                break;

            case RangeType.none:
            default:
                return false;
        }

        sequence.add(from, to, digits);
        return true;
    }

    // ------------------------------------------------------------------------
    // Preview

    public boolean showPreview() {
        String uriString;
        int index;
        int count;

        EditText uriEditor = (EditText) view.findViewById(R.id.batch_seq_uri_editor);

        uriString = uriEditor.getText().toString();
        if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            if (uri.getScheme() == null) {
                errorMessage = activity.getString(R.string.batch_seq_msg_uri_not_valid);
                preview.setText(errorMessage);
                return false;
            }
        }

        for (count = 0, index = uriString.length() - 1; index >= 0; index--) {
            if (uriString.charAt(index) == '*') {
                count++;
            }
        }

        if (count == 0) {
            errorMessage = activity.getString(R.string.batch_seq_msg_no_wildcard);
            preview.setText(errorMessage);
            return false;
        }

        sequence.clear();
        addRange(0);
        addRange(1);
        addRange(2);

        String[] uriArray = sequence.getPreview(uriString);

        if (uriArray.length == 0) {
            errorMessage = activity.getString(R.string.batch_seq_msg_no_from_to);
            preview.setText(errorMessage);
            return false;
        } else {
            preview.setText("");
            for (index = 0; index < uriArray.length; index++)
                preview.append(uriArray[index] + "\n");
            errorMessage = null;
        }
        return true;
    }

    // --------------------------------------------------------------
    // UriBatchInterface

    public long batchStart() {
        if (showPreview()) {
            EditText uriEditor = (EditText) view.findViewById(R.id.batch_seq_uri_editor);
            return sequence.batchStart(uriEditor.getText().toString());
        }
        return 0;
    }

    public String batchGet1(long resultOfBatchStart) {
        if (resultOfBatchStart != 0)
            return sequence.batchGetUri(resultOfBatchStart);
        return null;
    }

    public void batchEnd(long resultOfBatchStart) {
        if (resultOfBatchStart != 0)
            sequence.batchEnd(resultOfBatchStart);
    }

    public int    batchCount() {
        EditText uriEditor = (EditText) view.findViewById(R.id.batch_seq_uri_editor);
        return sequence.count(uriEditor.getText().toString());
    }
}
