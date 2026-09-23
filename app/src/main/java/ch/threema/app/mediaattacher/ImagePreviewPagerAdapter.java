package ch.threema.app.mediaattacher;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import ch.threema.app.activities.MediaViewerActivity;

public class ImagePreviewPagerAdapter extends FragmentStateAdapter {
    private List<MediaAttachItem> mediaAttachItems = new ArrayList<>();

    public ImagePreviewPagerAdapter(FragmentActivity mediaSelectionBaseActivity) {
        super(mediaSelectionBaseActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        MediaAttachItem mediaAttachItem = getItem(position);
        if (mediaAttachItem != null) {
            int mediaType = mediaAttachItem.getType();
            Bundle args = new Bundle();
            args.putBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, true);

            Fragment fragment = null;
            if (mediaType == MediaAttachItem.TYPE_IMAGE || mediaType == MediaAttachItem.TYPE_GIF || mediaType == MediaAttachItem.TYPE_WEBP) {
                fragment = new ImagePreviewFragment(mediaAttachItem);
            } else if (mediaType == MediaAttachItem.TYPE_VIDEO) {
                fragment = new VideoPreviewFragment(mediaAttachItem);
            }

            if (fragment != null) {
                fragment.setArguments(args);
                return fragment;
            }
        }
        return new DummyPreviewFragment();
    }

    public void setMediaItems(List<MediaAttachItem> items) {
        mediaAttachItems = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mediaAttachItems.size();
    }

    @Nullable
    public MediaAttachItem getItem(int position) {
        if (position < mediaAttachItems.size() && position >= 0) {
            return mediaAttachItems.get(position);
        }
        return null;
    }
}
