package sk.martin64.partycast.ui.connect;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputLayout;

import java.io.PrintWriter;
import java.io.StringWriter;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import partycast.model.Lobby;
import sk.martin64.partycast.BuildConfig;
import sk.martin64.partycast.ConnectActivity;
import sk.martin64.partycast.MainActivity;
import sk.martin64.partycast.R;
import sk.martin64.partycast.ServerLobbyService;
import sk.martin64.partycast.utils.Callback;

@SuppressLint("NonConstantResourceId")
public class ConnectManuallyFragment extends Fragment {

    @BindView(R.id.input_server)
    TextInputLayout inputServer;
    @BindView(R.id.input_name)
    TextInputLayout inputName;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.button)
    Button button;

    Unbinder unbinder;
    private ConnectActivity activity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_connect_manually, container, false);
        unbinder = ButterKnife.bind(this, v);
        activity = (ConnectActivity) getActivity();

        inputServer.getEditText().setText(activity.savedInstance.getString("last_server", ""));
        inputName.getEditText().setText(ServerLobbyService.pickName(activity.savedInstance.getString("last_name", null)));

        button.setOnClickListener(v1 -> {
            ProgressDialog dialog = new ProgressDialog(v1.getContext());
            dialog.setTitle("Connecting to the server...");
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setCancelable(false);
            dialog.show();

            activity.savedInstance.edit()
                    .putString("last_server", inputServer.getEditText().getText().toString())
                    .putString("last_name", inputName.getEditText().getText().toString())
                    .apply();

            activity.coordinatorService.connectClient(activity,
                    inputServer.getEditText().getText().toString(),
                    inputName.getEditText().getText().toString(),
                    new Callback<Lobby>() {
                        @Override
                        public void onError(Exception e) {
                            if (!activity.isDestroyed()) {
                                dialog.dismiss();

                                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                                builder.setTitle("Failed to connect to the server");
                                if (BuildConfig.DEBUG) {
                                    StringWriter errors = new StringWriter();
                                    e.printStackTrace(new PrintWriter(errors));
                                    builder.setMessage(errors.toString());
                                } else builder.setMessage(e.getMessage());
                                builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
                                builder.show();
                            }
                        }

                        @Override
                        public void onSuccess(Lobby lobby) {
                            dialog.dismiss();
                            startActivity(new Intent(activity, MainActivity.class));
                            activity.finish();
                        }
                    });
        });

        return v;
    }
}