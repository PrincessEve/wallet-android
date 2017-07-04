package com.mycelium.wallet.activity.rmc;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.activity.modern.RecordRowBuilder;
import com.mycelium.wallet.activity.rmc.json.CreateRmcOrderResponse;
import com.mycelium.wallet.colu.ColuAccount;
import com.mycelium.wallet.colu.ColuManager;
import com.mycelium.wallet.event.AccountChanged;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.api.response.Feature;
import com.mycelium.wapi.wallet.WalletAccount;
import com.squareup.otto.Bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by elvis on 20.06.17.
 */

public class ChooseRMCAccountFragment extends Fragment {

    private static final String TAG = "ChooseRMCAccount";
    String rmcCount = "0";
    String btcCount;
    String payMethod;
    String coluAddress;
    private MbwManager _mbwManager;

    @BindView(R.id.create_new_rmc)
    protected View createRmcAccount;
    @BindView(R.id.new_rmc_account)
    protected View newRmcAccount;

    @BindView(R.id.rmc_accounts)
    LinearLayout accountList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            rmcCount = getArguments().getString(Keys.RMC_COUNT);
            payMethod = getArguments().getString(Keys.PAY_METHOD);
            btcCount = getArguments().getString(Keys.BTC_COUNT);
        }
        _mbwManager = MbwManager.getInstance(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rmc_choose_account, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ButterKnife.bind(this, view);
        newRmcAccount.setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.rmcCount)).setText(rmcCount + " RMC");
        List<Map.Entry<UUID, WalletAccount>> accountsList = new ArrayList<>();
        for (Map.Entry<UUID, WalletAccount> uuidWalletAccountEntry : _mbwManager.getColuManager().getAccounts().entrySet()) {
            if (((ColuAccount) uuidWalletAccountEntry.getValue()).getColuAsset() == ColuAccount.ColuAsset.RMC) {
                accountsList.add(uuidWalletAccountEntry);
            }
        }
        if (accountsList.isEmpty()) {
            createRmcAccount.setVisibility(View.VISIBLE);
            accountList.setVisibility(View.GONE);
        } else {
            createRmcAccount.setVisibility(View.GONE);
            accountList.setVisibility(View.VISIBLE);
            accountList.removeAllViews();
            for (final Map.Entry<UUID, WalletAccount> uuidWalletAccountEntry : accountsList) {
                View accountView = new RecordRowBuilder(_mbwManager, getResources(), LayoutInflater.from(getActivity()))
                        .buildRecordView(accountList, uuidWalletAccountEntry.getValue(), true, true);
                accountView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        UUID accountID = uuidWalletAccountEntry.getKey();
                        ColuAccount account = _mbwManager.getColuManager().getAccount(accountID);
                        coluAddress = account.getReceivingAddress().get().toString();
                        clickYes();
                    }
                });
                accountList.addView(accountView);
            }
        }

    }

    @OnClick(R.id.btCreateNew)
    void clickCreateAcc() {
        createRmcAccount.setVisibility(View.GONE);
        _mbwManager.getVersionManager().showFeatureWarningIfNeeded(
                getActivity(), Feature.COLU_NEW_ACCOUNT, true, new Runnable() {
                    @Override
                    public void run() {
                        _mbwManager.runPinProtectedFunction(getActivity(), new Runnable() {
                            @Override
                            public void run() {
                                createColuAccount(ColuAccount.ColuAsset.RMC, new Callback() {
                                    @Override
                                    public void created(UUID accountID) {
                                        accountAddressForAccept(accountID);
                                    }
                                });
                            }
                        });
                    }
                }
        );

    }

    private void accountAddressForAccept(UUID accountID) {
        newRmcAccount.setVisibility(View.VISIBLE);
        ColuAccount account = _mbwManager.getColuManager().getAccount(accountID);
        coluAddress = account.getReceivingAddress().get().toString();
        ((TextView) newRmcAccount.findViewById(R.id.address)).setText(account.getReceivingAddress().get().toString());
    }

    private void createColuAccount(final ColuAccount.ColuAsset coluAsset, final Callback created) {

        AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
        b.setTitle(getString(R.string.colu));
        View diaView = LayoutInflater.from(getActivity()).inflate(R.layout.ext_colu_tos, null);
        b.setView(diaView);
        b.setPositiveButton(getString(R.string.agree), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Create the account initially without set email address
                // if needed, the user can later set and verify it via account menu.
                // for now we hard code asset = MT
                new AddColuAsyncTask(_mbwManager.getEventBus(), coluAsset, created).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
        b.setNegativeButton(getString(R.string.dontagree), null);

        AlertDialog dialog = b.create();

        dialog.show();
    }


    class RmsApiTask extends AsyncTask<Void, Void, CreateRmcOrderResponse.Json> {

        private String amount;
        private String amountInRmc;
        private String assetAddress;
        private String paymentMethod;

        public RmsApiTask(String amount, String amountInRmc, String assetAddress, String paymentMethod) {
            this.amount = amount;
            this.amountInRmc = amountInRmc;
            this.assetAddress = assetAddress;
            this.paymentMethod = paymentMethod;
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected CreateRmcOrderResponse.Json doInBackground(Void... params) {
            RmcApiClient client = new RmcApiClient(_mbwManager.getNetwork());
            CreateRmcOrderResponse.Json orderResponse = client.createOrder(amount, amountInRmc, assetAddress, paymentMethod);
            return orderResponse;
        }

        @Override
        protected void onPostExecute(CreateRmcOrderResponse.Json result) {
            //Address should be funded to get tokens
            String fundingAddress = result.order.paymentDetails.address;
        }
    }

    @OnClick(R.id.btYes)
    void clickYes() {
        if (payMethod.equals("BTC")) {
            RmsApiTask task = new RmsApiTask(btcCount,rmcCount, coluAddress, payMethod);
            task.execute();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("bitcoin:13sTW2pA3U8LwixoSapi92LsXyjyXPYhA3?amount=0.004179&r=https%3A%2F%2Fbitpay.com%2Fi%2FMLdKWpRhJXcTv8NKFGLPhT")));
        } else if (payMethod.equals("ETH")) {
            Intent intent = new Intent(getActivity(), EthPaymentRequestActivity.class);
            intent.putExtra(Keys.RMC_COUNT, rmcCount);
            startActivity(intent);
        } else {
            Intent intent = new Intent(getActivity(), BankPaymentRequestActivity.class);
            intent.putExtra(Keys.RMC_COUNT, rmcCount);
            startActivity(intent);
        }
    }

    @OnClick(R.id.btNo)
    void clickNo() {
        getActivity().finish();
    }

    interface Callback {
        void created(UUID account);
    }

    private class AddColuAsyncTask extends AsyncTask<Void, Integer, UUID> {
        private final boolean alreadyHadColuAccount;
        private Bus bus;
        private final ColuAccount.ColuAsset coluAsset;
        private ColuManager coluManager;
        private final ProgressDialog progressDialog;
        private Callback created;

        public AddColuAsyncTask(Bus bus, ColuAccount.ColuAsset coluAsset, Callback created) {
            this.bus = bus;
            this.coluAsset = coluAsset;
            this.created = created;
            this.alreadyHadColuAccount = _mbwManager.getMetadataStorage().isPairedService(MetadataStorage.PAIRED_SERVICE_COLU);
            progressDialog = ProgressDialog.show(getActivity(), getString(R.string.colu), getString(R.string.colu_create_account, coluAsset.label));
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.show();
        }

        @Override
        protected UUID doInBackground(Void... params) {
            _mbwManager.getMetadataStorage().setPairedService(MetadataStorage.PAIRED_SERVICE_COLU, true);
            coluManager = _mbwManager.getColuManager();
            if (coluManager == null) {
                Log.d(TAG, "Error could not obtain coluManager !");
                return null;
            } else {
                try {
                    UUID uuid = coluManager.enableAsset(coluAsset, null);
                    coluManager.scanForAccounts();
                    return uuid;
                } catch (Exception e) {
                    Log.d(TAG, "Error while creating Colu account for asset " + coluAsset.name + ": " + e.getMessage());
                    return null;
                }
            }
        }


        @Override
        protected void onPostExecute(UUID account) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            if (account != null) {
                _mbwManager.addExtraAccounts(coluManager);
                bus.post(new AccountChanged(account));
                created.created(account);
            } else {
                // something went wrong - clean up the half ready coluManager
                Toast.makeText(getActivity(), R.string.colu_unable_to_create_account, Toast.LENGTH_SHORT).show();
                _mbwManager.getMetadataStorage().setPairedService(MetadataStorage.PAIRED_SERVICE_COLU, alreadyHadColuAccount);
            }
        }
    }
}
