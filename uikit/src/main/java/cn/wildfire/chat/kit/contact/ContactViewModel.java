/*
 * Copyright (c) 2020 WildFireChat. All rights reserved.
 */

package cn.wildfire.chat.kit.contact;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cn.wildfire.chat.kit.Config;
import cn.wildfire.chat.kit.WfcUIKit;
import cn.wildfire.chat.kit.common.OperateResult;
import cn.wildfire.chat.kit.contact.model.UIUserInfo;
import cn.wildfirechat.model.Conversation;
import cn.wildfirechat.model.DomainInfo;
import cn.wildfirechat.model.FriendRequest;
import cn.wildfirechat.model.UserInfo;
import cn.wildfirechat.remote.ChatManager;
import cn.wildfirechat.remote.GeneralCallback;
import cn.wildfirechat.remote.GetRemoteDomainsCallback;
import cn.wildfirechat.remote.OnFriendUpdateListener;
import cn.wildfirechat.remote.SearchUserCallback;
import cn.wildfirechat.remote.StringListCallback;

public class ContactViewModel extends ViewModel implements OnFriendUpdateListener {
    private MutableLiveData<List<UIUserInfo>> contactListLiveData;
    private MutableLiveData<Integer> friendRequestUpdatedLiveData;
    private MutableLiveData<List<UIUserInfo>> favContactListLiveData;

    public ContactViewModel() {
        super();
        ChatManager.Instance().addFriendUpdateListener(this);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ChatManager.Instance().removeFriendUpdateListener(this);
    }

    public MutableLiveData<List<UIUserInfo>> contactListLiveData() {
        if (contactListLiveData == null) {
            contactListLiveData = new MutableLiveData<>();
        }
        reloadContact();
        return contactListLiveData;
    }

    public MutableLiveData<Integer> friendRequestUpdatedLiveData() {
        if (friendRequestUpdatedLiveData == null) {
            friendRequestUpdatedLiveData = new MutableLiveData<>();
        }
        ChatManager.Instance().getWorkHandler().post(() -> {
            int count = getUnreadFriendRequestCount();
            friendRequestUpdatedLiveData.postValue(count);
        });
        return friendRequestUpdatedLiveData;
    }

    public MutableLiveData<List<UIUserInfo>> favContactListLiveData() {
        if (favContactListLiveData == null) {
            favContactListLiveData = new MutableLiveData<>();
        }
        reloadFavContact();
        return favContactListLiveData;
    }

    public void reloadFriendRequestStatus() {
        if (friendRequestUpdatedLiveData != null) {
            ChatManager.Instance().getWorkHandler().post(() -> {
                int count = getUnreadFriendRequestCount();
                friendRequestUpdatedLiveData.postValue(count);
            });
        }
    }

    public void reloadFavContact() {
        ChatManager.Instance().getFavUsers(new StringListCallback() {
            @Override
            public void onSuccess(List<String> userIds) {
                if (userIds == null || userIds.isEmpty()) {
                    return;
                }
                List<UserInfo> userInfos = ChatManager.Instance().getUserInfos(userIds, null);
                if (userInfos != null){
                    favContactListLiveData.postValue(UIUserInfo.fromUserInfos(userInfos, true));
                }
            }

            @Override
            public void onFail(int errorCode) {

            }
        });
    }

    public List<String> getFriends(boolean refresh) {
        return ChatManager.Instance().getMyFriendList(refresh);
    }

    private AtomicInteger loadingCount = new AtomicInteger(0);

    public void reloadContact() {
        int count = loadingCount.get();
        if (count > 0) {
            return;
        }
        loadingCount.incrementAndGet();
        ChatManager.Instance().getWorkHandler().post(() -> {
            SharedPreferences sp = WfcUIKit.getWfcUIKit().getApplication().getSharedPreferences("wfc_kit_config", Context.MODE_PRIVATE);
            boolean pcLogined = sp.getBoolean("wfc_uikit_had_pc_session", false);
            UserInfo fileHelpUserInfo = null;
            if (pcLogined) {
                fileHelpUserInfo = ChatManager.Instance().getUserInfo(Config.FILE_TRANSFER_ID, true);
            }

            List<UserInfo> userInfos = ChatManager.Instance().getMyFriendListInfo(false);
            if (fileHelpUserInfo != null && userInfos != null) {
                userInfos.add(fileHelpUserInfo);
            }
            if (contactListLiveData != null && userInfos != null) {
                contactListLiveData.postValue(UIUserInfo.fromUserInfos(userInfos));
            }
            loadingCount.decrementAndGet();
        });
    }

    public List<UserInfo> getContacts(boolean refresh) {
        return ChatManager.Instance().getMyFriendListInfo(refresh);
    }

    public int getUnreadFriendRequestCount() {
        return ChatManager.Instance().getUnreadFriendRequestStatus();
    }

    public void clearUnreadFriendRequestStatus() {
        ChatManager.Instance().clearUnreadFriendRequestStatus();
    }

    @Override
    public void onFriendListUpdate(List<String> updateFriendList) {
        reloadContact();
    }

    @Override
    public void onFriendRequestUpdate(List<String> newRequests) {
        if (friendRequestUpdatedLiveData != null) {
            friendRequestUpdatedLiveData.setValue(getUnreadFriendRequestCount());
        }
    }

    public List<FriendRequest> getFriendRequest() {
        return ChatManager.Instance().getFriendRequest(true);
    }

    public MutableLiveData<Boolean> acceptFriendRequest(String friendId) {

        MutableLiveData<Boolean> result = new MutableLiveData<>();
        ChatManager.Instance().handleFriendRequest(friendId, true, null, new GeneralCallback() {
            @Override
            public void onSuccess() {
                ChatManager.Instance().loadFriendRequestFromRemote();
                List<FriendRequest> inComingFriendRequests = ChatManager.Instance().getFriendRequest(true);
                for (FriendRequest request : inComingFriendRequests) {
                    if (request.target.equals(friendId)) {
                        result.setValue(true);
                        return;
                    }
                }
                result.setValue(false);
            }

            @Override
            public void onFail(int errorCode) {
                result.setValue(false);
            }
        });
        return result;
    }

    public MutableLiveData<List<UserInfo>> searchUser(String keyword, ChatManager.SearchUserType searchUserType, int page) {
        MutableLiveData<List<UserInfo>> result = new MutableLiveData<>();
        ChatManager.Instance().searchUser(keyword, searchUserType, page, new SearchUserCallback() {
            @Override
            public void onSuccess(List<UserInfo> userInfos) {
                result.setValue(userInfos);
            }

            @Override
            public void onFail(int errorCode) {
                result.setValue(null);
            }
        });

        return result;
    }

    public boolean isFriend(String targetUid) {
        return ChatManager.Instance().isMyFriend(targetUid);
    }

    public boolean isBlacklisted(String targetUid) {
        return ChatManager.Instance().isBlackListed(targetUid);
    }

    public boolean isFav(String targetUid) {
        return ChatManager.Instance().isFavUser(targetUid);
    }

    public LiveData<OperateResult<Boolean>> deleteFriend(String userId) {
        MutableLiveData<OperateResult<Boolean>> result = new MutableLiveData<>();
        ChatManager.Instance().deleteFriend(userId, new GeneralCallback() {
            @Override
            public void onSuccess() {
                ChatManager.Instance().removeConversation(new Conversation(Conversation.ConversationType.Single, userId, 0), true);
                result.postValue(new OperateResult<>(0));
            }

            @Override
            public void onFail(int errorCode) {
                result.postValue(new OperateResult<>(errorCode));
            }
        });

        return result;
    }

    public LiveData<OperateResult<Boolean>> setBlacklist(String userId, boolean value) {
        MutableLiveData<OperateResult<Boolean>> result = new MutableLiveData<>();
        ChatManager.Instance().setBlackList(userId, value, new GeneralCallback() {
            @Override
            public void onSuccess() {
                result.postValue(new OperateResult<>(0));
            }

            @Override
            public void onFail(int errorCode) {
                result.postValue(new OperateResult<>(errorCode));
            }
        });

        return result;
    }

    public LiveData<OperateResult<Boolean>> setFav(String userId, boolean fav) {
        MutableLiveData<OperateResult<Boolean>> result = new MutableLiveData<>();
        ChatManager.Instance().setFavUser(userId, fav, new GeneralCallback() {
            @Override
            public void onSuccess() {
                result.postValue(new OperateResult<>(0));
            }

            @Override
            public void onFail(int errorCode) {
                result.postValue(new OperateResult<>(errorCode));
            }
        });

        return result;
    }

    public MutableLiveData<Boolean> invite(String targetUid, String message) {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        ChatManager.Instance().sendFriendRequest(targetUid, message, null, new GeneralCallback() {
            @Override
            public void onSuccess() {
                result.setValue(true);
            }

            @Override
            public void onFail(int errorCode) {
                result.setValue(false);
            }
        });
        return result;
    }

    public MutableLiveData<OperateResult<Integer>> setFriendAlias(String userId, String alias) {
        MutableLiveData<OperateResult<Integer>> data = new MutableLiveData<>();
        ChatManager.Instance().setFriendAlias(userId, alias, new GeneralCallback() {
            @Override
            public void onSuccess() {
                data.setValue(new OperateResult<>(0));
            }

            @Override
            public void onFail(int errorCode) {
                data.setValue(new OperateResult<>(errorCode));
            }
        });
        return data;
    }

    public MutableLiveData<List<DomainInfo>> loadRemoteDomains() {
        MutableLiveData<List<DomainInfo>> data = new MutableLiveData<>();
        ChatManager.Instance().loadRemoteDomains(new GetRemoteDomainsCallback() {
            @Override
            public void onSuccess(List<DomainInfo> domainInfos) {
                data.setValue(domainInfos);
            }

            @Override
            public void onFail(int errorCode) {
                data.setValue(null);
            }
        });
        return data;
    }

    public String getFriendAlias(String userId) {
        return ChatManager.Instance().getFriendAlias(userId);
    }

}
