package sk.martin64.partycast.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import sk.martin64.partycast.R;
import partycast.client.ClientLobbyMember;
import partycast.model.Lobby;
import partycast.model.LobbyMember;
import sk.martin64.partycast.utils.Callback;

import static android.content.Context.MODE_PRIVATE;

public class LobbyMemberDialog extends BottomSheetDialogFragment {

    @BindView(R.id.imageView)
    ImageView imageView;
    @BindView(R.id.imageButton)
    Button imageButton;
    @BindView(R.id.input_name)
    TextInputLayout inputName;
    @BindView(R.id.switch1)
    MaterialCheckBox switch1;
    @BindView(R.id.switch2)
    MaterialCheckBox switch2;
    @BindView(R.id.switch3)
    MaterialCheckBox switch3;
    @BindView(R.id.switch4)
    MaterialCheckBox switch4;
    @BindView(R.id.switch5)
    MaterialCheckBox switch5;
    @BindView(R.id.button5)
    Button buttonKick;

    private Unbinder unbinder;
    private LobbyMember member;

    public LobbyMemberDialog(LobbyMember member) {
        this.member = member;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.bottom_lobby_member, container, false);
        unbinder = ButterKnife.bind(this, root);

        inputName.getEditText().setText(member.getName());
        imageView.setImageResource(ClientLobbyMember.getAgentIcon(member));

        switch1.setChecked(Lobby.checkPermission(member, LobbyMember.PERMISSION_CHANGE_NAME));
        switch2.setChecked(Lobby.checkPermission(member, LobbyMember.PERMISSION_QUEUE));
        switch3.setChecked(Lobby.checkPermission(member, LobbyMember.PERMISSION_MEMBER_LIST));
        switch4.setChecked(Lobby.checkPermission(member, LobbyMember.PERMISSION_MANAGE_USERS));
        switch5.setChecked(Lobby.checkPermission(member, LobbyMember.PERMISSION_MANAGE_QUEUE));

        LobbyMember selfUser = member.getContext().getClient();
        boolean isSelf = Lobby.isSelf(member);

        inputName.setEnabled(isSelf || Lobby.checkPermission(selfUser, LobbyMember.PERMISSION_MANAGE_USERS));
        imageButton.setEnabled(isSelf || Lobby.checkPermission(selfUser, LobbyMember.PERMISSION_MANAGE_USERS));
        switch1.setEnabled(!isSelf && Lobby.checkPermission(selfUser, LobbyMember.PERMISSION_MANAGE_USERS));
        switch2.setEnabled(!isSelf && Lobby.checkPermission(selfUser, LobbyMember.PERMISSION_MANAGE_USERS));
        switch3.setEnabled(!isSelf && Lobby.checkPermission(selfUser, LobbyMember.PERMISSION_MANAGE_USERS));
        switch4.setEnabled(!isSelf && Lobby.checkPermission(selfUser, LobbyMember.PERMISSION_OWNER));
        switch5.setEnabled(!isSelf && Lobby.checkPermission(selfUser, LobbyMember.PERMISSION_OWNER));

        imageButton.setOnClickListener(v -> {
            if (!inputName.getEditText().getText().toString().equals(member.getName())) {
                if (isSelf) {
                    getActivity().getSharedPreferences("si", MODE_PRIVATE)
                            .edit()
                            .putString("last_name", inputName.getEditText().getText().toString())
                            .apply();
                }
                member.changeName(inputName.getEditText().getText().toString(), null);
            }

            int perms = 0;
            if (switch1.isChecked()) perms |= LobbyMember.PERMISSION_CHANGE_NAME;
            if (switch2.isChecked()) perms |= LobbyMember.PERMISSION_QUEUE;
            if (switch3.isChecked()) perms |= LobbyMember.PERMISSION_MEMBER_LIST;
            if (switch4.isChecked()) perms |= LobbyMember.PERMISSION_MANAGE_USERS;
            if (switch5.isChecked()) perms |= LobbyMember.PERMISSION_MANAGE_QUEUE;

            if (perms != member.getPermissions()) {
                member.changePermissions(perms, null);
            }

            dismissAllowingStateLoss();
        });

        buttonKick.setOnClickListener(v -> {
            member.kick(new Callback<Void>() {
                @Override
                public void onError(Exception e) {
                    v.post(() ->
                            Toast.makeText(v.getContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onSuccess(Void aVoid) {
                    v.post(() -> {
                        Toast.makeText(v.getContext(), "User has been kicked", Toast.LENGTH_SHORT).show();
                        dismissAllowingStateLoss();
                    });
                }
            });
        });

        return root;
    }

    @Override
    public void onDestroy() {
        unbinder.unbind();
        super.onDestroy();
    }
}