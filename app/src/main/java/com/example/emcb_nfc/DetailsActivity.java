package com.example.emcb_nfc;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.st.st25sdk.NFCTag;
import com.st.st25sdk.STException;
import com.st.st25sdk.TagHelper;
import com.st.st25sdk.type5.st25dv.ST25DVTag;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.example.emcb_nfc.TagDiscovery.TAG;

public class DetailsActivity extends AppCompatActivity implements TagDiscovery.onTagDiscoveryCompletedListener{
    private NfcAdapter mNfcAdapter;
    private NFCTag mNfcTag;
    private ST25DVTag mST25DVTag;

    private EditText mSellingDateEdit;
    private EditText mCustomerNameEdit;
    private EditText mRepairStatusEdit;
    private EditText mProductionDateEdit;
    private EditText mDistributorNameEdit;
    private Button mReadMemoryBtn;
    private Button mWriteMemoryBtn;

    private Vibrator vibrator;

    private int dataStartAddress = 256;
    private int dataEndAddress = 295;
    private int data2StartAddress = 384;
    private int data2EndAddress = 511;

    String sellingDate;
    String customerName;
    String repairStatus;
    String productionDate;
    String distributorName;

    byte[] sellingDateByte = new byte[8];
    byte[] customerNameByte = new byte[16];
    byte[] repairStatusByte = new byte[16];
    byte[] productionDateByte = new byte[8];
    byte[] distributorNameByte = new byte[16];

    private byte[] mPassword = {0,0,0,0,0,0,0,0};


    enum Action {
        READ_TAG_MEMORY,
        WRITE_TAG_MEMORY,
        PRESENT_PASSWORD
    }

    enum ActionStatus {
        ACTION_SUCCESSFUL,
        ACTION_FAILED,
        TAG_NOT_IN_THE_FIELD
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        mSellingDateEdit = (EditText) findViewById(R.id.sellingDateInput);
        mCustomerNameEdit = (EditText) findViewById(R.id.customerNameInput);
        mRepairStatusEdit = (EditText) findViewById(R.id.repairStatusInput);
        mProductionDateEdit = (EditText) findViewById(R.id.productionDateInput);
        mDistributorNameEdit = (EditText) findViewById(R.id.distributorNameInput);
        mReadMemoryBtn = findViewById(R.id.readMemoryBtn);
        mReadMemoryBtn.setOnClickListener(view ->  {
            if(mNfcTag != null) {
                executeAsynchronousAction(Action.READ_TAG_MEMORY);
            } else {
                buttonStatus(false);
                Toast.makeText(this, "Action failed!", Toast.LENGTH_LONG).show();
            }
        });

        mWriteMemoryBtn = findViewById(R.id.writeMemoryBtn);
        mWriteMemoryBtn.setOnClickListener(view ->  {
            if(mNfcTag != null) {
                inputPasswordDialog();
            } else {
                buttonStatus(false);
                Toast.makeText(this, "Action failed!", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if if this phone has NFC hardware
        if (mNfcAdapter == null) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            // set title
            alertDialogBuilder.setTitle("Warning!");
            // set dialog message
            alertDialogBuilder
                    .setMessage("This phone doesn't have NFC hardware!")
                    .setCancelable(true)
                    .setPositiveButton("Leave", (dialog, id) -> {
                        dialog.cancel();
                        finish();
                    });
            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();
            // show it
            alertDialog.show();
        } else {
            //Toast.makeText(this, "We are ready to play with NFC!", Toast.LENGTH_SHORT).show();
            // Give priority to the current activity when receiving NFC events (over other actvities)
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            IntentFilter[] nfcFilters = null;
            String[][] nfcTechLists = null;
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, nfcFilters, nfcTechLists);
        }
        // The current activity can be resumed for several reasons (NFC tag tapped is one of them).
        // Check what was the reason which triggered the resume of current application
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED) ||
                action.equals(NfcAdapter.ACTION_TECH_DISCOVERED) ||
                action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            // If the resume was triggered by an NFC event, it will contain an EXTRA_TAG providing
            // the handle of the NFC Tag
            Tag androidTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (androidTag != null) {
                // This action will be done in an Asynchronous task.
                // onTagDiscoveryCompleted() of current activity will be called when the discovery is completed.
                new TagDiscovery(this).execute(androidTag);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_eeprom:
                Intent intent = new Intent(this, MainActivity.class);
                intent.setAction(Intent.ACTION_DEFAULT);
                startActivity(intent);
                return true;
            case R.id.menu_details:
                return true;
            case R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // onResume() gets called after this to handle the intent
        setIntent(intent);
    }

    @Override
    public void onTagDiscoveryCompleted(NFCTag nfcTag, TagHelper.ProductID productId, STException error) {
        if (error != null) {
            buttonStatus(false);
            Toast.makeText(getApplication(), "Error while reading the tag: " + error.toString(), Toast.LENGTH_LONG).show();
            return;
        }
        if (nfcTag != null) {
            mNfcTag = nfcTag;
            mST25DVTag = (ST25DVTag) mNfcTag;
            try {
                String uidString = nfcTag.getUidString();
                buttonStatus(true);
            } catch (STException e) {
                e.printStackTrace();
                buttonStatus(false);
                Toast.makeText(this, "Discovery successful but failed to read the tag!", Toast.LENGTH_LONG).show();
            }
        } else {
            buttonStatus(false);
            Toast.makeText(this, "Tag discovery failed!", Toast.LENGTH_LONG).show();
        }
    }

    private void executeAsynchronousAction(Action action) {
        Log.d(TAG, "Starting background action " + action);
        new myAsyncTask(action).execute();
    }

    private class myAsyncTask extends AsyncTask<Void, Void, ActionStatus> {
        Action mAction;
        byte[] data;
        byte[] data2;

        public myAsyncTask(Action action) {
            mAction = action;
        }

        @Override
        protected ActionStatus doInBackground(Void... param) {
            ActionStatus result;

            try {
                switch (mAction) {
                    case READ_TAG_MEMORY:
                        data = mNfcTag.readBytes(dataStartAddress,dataEndAddress);
                        data2 = mNfcTag.readBytes(data2StartAddress,data2EndAddress);
                        sellingDate = new String(data, 0, 8, StandardCharsets.UTF_8);
                        customerName = new String(data, 8, 16, StandardCharsets.UTF_8);
                        repairStatus = new String(data, 24, 16, StandardCharsets.UTF_8);
                        sellingDateByte = Arrays.copyOfRange(data, 0, 8);
                        customerNameByte = Arrays.copyOfRange(data, 8, 24);
                        repairStatusByte = Arrays.copyOfRange(data, 24, 40);
                        productionDate = new String(data2, 0, 8, StandardCharsets.UTF_8);
                        distributorName = new String(data2, 8, 16, StandardCharsets.UTF_8);
                        productionDateByte = Arrays.copyOfRange(data2, 0, 8);;
                        distributorNameByte = Arrays.copyOfRange(data2, 8, 24);;
                        // If we get to this point, it means that no STException occured so the action was successful
                        result = ActionStatus.ACTION_SUCCESSFUL;
                        break;

                    case WRITE_TAG_MEMORY:
                        sellingDateByte = StandardCharsets.US_ASCII.encode(mSellingDateEdit.getText().toString()).array();
                        customerNameByte = StandardCharsets.US_ASCII.encode(mCustomerNameEdit.getText().toString()).array();
                        repairStatusByte = StandardCharsets.US_ASCII.encode(mRepairStatusEdit.getText().toString()).array();
                        if(mST25DVTag.isMailboxEnabled(true)) {
                            mST25DVTag.disableMailbox();
                        }
                        mNfcTag.writeBytes(dataStartAddress, sellingDateByte);
                        mNfcTag.writeBytes(dataStartAddress + 8, customerNameByte);
                        mNfcTag.writeBytes(dataStartAddress + 24, repairStatusByte);
                        mST25DVTag.enableMailbox();
                        // If we get to this point, it means that no STException occured so the action was successful
                        result = ActionStatus.ACTION_SUCCESSFUL;
                        break;

                    case PRESENT_PASSWORD:
                        mST25DVTag.presentPassword(ST25DVTag.ST25DV_PASSWORD_2, mPassword);
                        result = ActionStatus.ACTION_SUCCESSFUL;
                        break;

                    default:
                        result = ActionStatus.ACTION_FAILED;
                        break;
                }

            } catch (STException e) {
                if (e.getError() == STException.STExceptionCode.TAG_NOT_IN_THE_FIELD) {
                    result = ActionStatus.TAG_NOT_IN_THE_FIELD;
                } else {
                    e.printStackTrace();
                    result = ActionStatus.ACTION_FAILED;
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(ActionStatus actionStatus) {
            vibrator.vibrate(100);
            switch(actionStatus) {
                case ACTION_SUCCESSFUL:
                    switch (mAction) {
                        case READ_TAG_MEMORY:
                            mSellingDateEdit.setText(sellingDate);
                            if(customerNameByte[0] == 0) {
                                mCustomerNameEdit.getText().clear();
                            } else {
                                mCustomerNameEdit.setText(customerName);
                            }
                            if(repairStatusByte[0] == 0) {
                                mRepairStatusEdit.getText().clear();
                            } else {
                                mRepairStatusEdit.setText(repairStatus);
                            }
                            if(productionDateByte[0] == 0) {
                                mProductionDateEdit.getText().clear();
                            } else {
                                mProductionDateEdit.setText(productionDate);
                            }
                            if(distributorNameByte[0] == 0) {
                                mDistributorNameEdit.getText().clear();
                            } else {
                                mDistributorNameEdit.setText(distributorName);
                            }
                            Toast.makeText(DetailsActivity.this, "Read successful", Toast.LENGTH_LONG).show();
                            break;
                        case WRITE_TAG_MEMORY:
                            Toast.makeText(DetailsActivity.this, "Write successful", Toast.LENGTH_LONG).show();
                            break;

                        case PRESENT_PASSWORD:
                            executeAsynchronousAction(Action.WRITE_TAG_MEMORY);
                            Toast.makeText(DetailsActivity.this, "Present Password successful", Toast.LENGTH_LONG).show();
                            break;
                    }
                    break;

                case ACTION_FAILED:
                    buttonStatus(false);
                    Toast.makeText(DetailsActivity.this, "Action failed!", Toast.LENGTH_LONG).show();
                    break;

                case TAG_NOT_IN_THE_FIELD:
                    buttonStatus(false);
                    Toast.makeText(DetailsActivity.this, "Tag not in the field!", Toast.LENGTH_LONG).show();
                    break;
            }

        }
    }

    private void buttonStatus(boolean status) {
        if(status) {
            mReadMemoryBtn.setClickable(status);
            mReadMemoryBtn.setBackgroundTintList(getColorStateList(R.color.button_color_state_enable));
            mWriteMemoryBtn.setClickable(status);
            mWriteMemoryBtn.setBackgroundTintList(getColorStateList(R.color.button_color_state_enable));
        } else {
            mReadMemoryBtn.setClickable(status);
            mReadMemoryBtn.setBackgroundTintList(getColorStateList(R.color.button_color_state_disable));
            mWriteMemoryBtn.setClickable(status);
            mWriteMemoryBtn.setBackgroundTintList(getColorStateList(R.color.button_color_state_disable));
        }

    }

    private void inputPasswordDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        EditText inputPassword = new EditText(this);
        dialog.setMessage("Input Password:");
        dialog.setView(inputPassword);
        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String inputRawPwd = inputPassword.getText().toString();
                for(int j = 0; j < 3; j++) {
                    mPassword[j + 5] = Byte.parseByte(inputRawPwd.substring(2*j, 2*j + 2));
                }
                executeAsynchronousAction(Action.PRESENT_PASSWORD);
            }
        });
        dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        dialog.show();
    }

}