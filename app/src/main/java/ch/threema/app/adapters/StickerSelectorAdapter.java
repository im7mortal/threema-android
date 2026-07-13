package ch.threema.app.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import ch.threema.android.LifecycleAwareAsyncTask;
import ch.threema.app.R;

public class StickerSelectorAdapter extends ArrayAdapter<String> {
    private String[] items;
    private LayoutInflater layoutInflater;

    private final AppCompatActivity activity;

    public StickerSelectorAdapter(AppCompatActivity activity, String[] items) {
        super(activity, R.layout.item_sticker_selector, items);

        this.activity = activity;
        this.items = items;
        this.layoutInflater = LayoutInflater.from(activity);
    }

    private class StickerSelectorHolder {
        ImageView imageView;
        int position;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        View itemView = convertView;
        final StickerSelectorHolder holder;

        if (convertView == null) {
            holder = new StickerSelectorHolder();

            // This a new view we inflate the new layout
            itemView = layoutInflater.inflate(R.layout.item_sticker_selector, parent, false);

            holder.imageView = itemView.findViewById(R.id.sticker);

            itemView.setTag(holder);
        } else {
            holder = (StickerSelectorHolder) itemView.getTag();
            holder.imageView.setImageBitmap(null);
        }

        final String item = items[position];
        holder.position = position;

        new LifecycleAwareAsyncTask<Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void params) {
                try {
                    return BitmapFactory.decodeStream(getContext().getAssets().open(item));
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    if (holder.position == position) {
                        holder.imageView.setImageBitmap(bitmap);
                    }
                }
            }

        }.execute(activity, null);

        return itemView;
    }

    public String getItem(int index) {
        return items[index];
    }
}
