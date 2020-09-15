package sk.martin64.partycast.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import sk.martin64.partycast.R;
import sk.martin64.partycast.client.ClientLobbyMember;
import sk.martin64.partycast.core.Lobby;
import sk.martin64.partycast.core.LobbyMember;

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

        return root;
    }

    @Override
    public void onDestroy() {
        unbinder.unbind();
        super.onDestroy();
    }
}