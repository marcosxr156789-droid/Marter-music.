package com.mastermusic.player;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 10;
    private final ArrayList<Song> songs = new ArrayList<>();
    private MediaPlayer player;
    private Equalizer equalizer;
    private int current = -1;
    private LinearLayout root, songList;
    private TextView nowTitle, nowArtist, status, lyricsText;
    private ImageView cover;
    private Button playButton;
    private SeekBar seekBar;
    private EditText lyricsInput;
    private android.content.SharedPreferences prefs;

    static class Song {
        long id, albumId;
        String title, artist;
        Uri uri;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("lyrics", MODE_PRIVATE);
        buildUi();
        requestAudioPermission();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(0xFFF5F0FF); t.setTextSize(size);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(0xFFFFFFFF);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF7C3AED));
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(0xFF100D16);
        scroll.addView(root);
        setContentView(scroll);

        TextView brand = text("MASTER MUSIC", 25);
        brand.setTypeface(null, 1);
        brand.setTextColor(0xFFB78BFA);
        root.addView(brand);
        TextView sub = text("Seu som. Offline. Do seu jeito.", 13);
        sub.setTextColor(0xFFB9AFC7);
        root.addView(sub);

        cover = new ImageView(this);
        cover.setImageResource(android.R.drawable.ic_media_play);
        cover.setBackgroundColor(0xFF251A35);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(190), dp(190));
        cp.gravity = Gravity.CENTER_HORIZONTAL; cp.topMargin = dp(20); cp.bottomMargin = dp(12);
        root.addView(cover, cp);

        nowTitle = text("Escolha uma música", 20);
        nowTitle.setGravity(Gravity.CENTER);
        nowTitle.setTypeface(null, 1);
        root.addView(nowTitle);
        nowArtist = text("Sua biblioteca local", 14);
        nowArtist.setGravity(Gravity.CENTER);
        nowArtist.setTextColor(0xFFC7BCD8);
        root.addView(nowArtist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = button("⏮");
        playButton = button("▶");
        Button next = button("⏭");
        controls.addView(prev); controls.addView(playButton); controls.addView(next);
        root.addView(controls);
        prev.setOnClickListener(v -> playIndex(Math.max(0, current - 1)));
        next.setOnClickListener(v -> playIndex(Math.min(songs.size() - 1, current + 1)));
        playButton.setOnClickListener(v -> togglePlayback());

        seekBar = new SeekBar(this);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFB78BFA));
        root.addView(seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && player != null) player.seekTo(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        status = text("Solicitando acesso às músicas…", 13);
        status.setTextColor(0xFFB78BFA);
        root.addView(status);

        TextView listHeading = text("MINHAS MÚSICAS", 17);
        listHeading.setTypeface(null, 1);
        listHeading.setPadding(0, dp(18), 0, dp(8));
        root.addView(listHeading);
        songList = new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);
        root.addView(songList);

        TextView eqHeading = text("EQUALIZADOR", 17);
        eqHeading.setTypeface(null, 1);
        eqHeading.setPadding(0, dp(20), 0, dp(8));
        root.addView(eqHeading);
        TextView eqNote = text("Ajuste as bandas abaixo enquanto uma música estiver tocando.", 12);
        eqNote.setTextColor(0xFFC7BCD8); root.addView(eqNote);
        addEqualizerControls();

        TextView lyricsHeading = text("LETRAS", 17);
        lyricsHeading.setTypeface(null, 1);
        lyricsHeading.setPadding(0, dp(20), 0, dp(8));
        root.addView(lyricsHeading);
        lyricsText = text("As letras são adicionadas manualmente nesta versão.", 13);
        root.addView(lyricsText);
        lyricsInput = new EditText(this);
        lyricsInput.setHint("Cole aqui a letra que você tem direito de usar…");
        lyricsInput.setHintTextColor(0xFF998DAA);
        lyricsInput.setTextColor(0xFFFFFFFF);
        lyricsInput.setMinLines(3);
        lyricsInput.setGravity(Gravity.TOP);
        root.addView(lyricsInput);
        Button saveLyrics = button("Salvar letra desta música");
        root.addView(saveLyrics);
        saveLyrics.setOnClickListener(v -> {
            if (current < 0 || current >= songs.size()) {
                Toast.makeText(this, "Escolha uma música primeiro.", Toast.LENGTH_SHORT).show(); return;
            }
            prefs.edit().putString("song_" + songs.get(current).id, lyricsInput.getText().toString()).apply();
            lyricsText.setText(lyricsInput.getText().toString().isEmpty() ? "Nenhuma letra salva." : lyricsInput.getText().toString());
            Toast.makeText(this, "Letra salva neste aparelho.", Toast.LENGTH_SHORT).show();
        });
    }

    private void addEqualizerControls() {
        String[] names = {"Graves", "Médios", "Agudos"};
        for (int i = 0; i < names.length; i++) {
            final int bandIndex = i;
            TextView label = text(names[i], 13);
            root.addView(label);
            SeekBar band = new SeekBar(this);
            band.setMax(100);
            band.setProgress(50);
            band.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFB78BFA));
            root.addView(band);
            band.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int progress, boolean user) {
                    if (!user || equalizer == null || equalizer.getNumberOfBands() == 0) return;
                    short min = equalizer.getBandLevelRange()[0], max = equalizer.getBandLevelRange()[1];
                    int count = equalizer.getNumberOfBands();
                    int bandNo = bandIndex == 0 ? 0 : (bandIndex == 1 ? count / 2 : count - 1);
                    short level = (short)(min + ((max - min) * progress / 100));
                    try { equalizer.setBandLevel((short)bandNo, level); } catch (Exception ignored) {}
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
        }
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33 ?
                "android.permission.READ_MEDIA_AUDIO" : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else requestPermissions(new String[]{permission}, REQUEST_AUDIO);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadSongs();
        else status.setText("Permissão negada. Permita acesso ao áudio nas configurações do app.");
    }

    private void loadSongs() {
        songs.clear();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID};
        try (Cursor c = getContentResolver().query(collection, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (c != null) while (c.moveToNext()) {
                Song s = new Song();
                s.id = c.getLong(0); s.title = c.getString(1); s.artist = c.getString(2);
                s.albumId = c.getLong(3);
                s.uri = ContentUris.withAppendedId(collection, s.id);
                if (s.title == null) s.title = "Faixa sem título";
                if (s.artist == null || s.artist.equals("<unknown>")) s.artist = "Artista desconhecido";
                songs.add(s);
            }
        } catch (Exception e) { status.setText("Não foi possível ler a biblioteca de áudio."); }
        songList.removeAllViews();
        if (songs.isEmpty()) status.setText("Nenhuma música encontrada. Copie arquivos de áudio para o celular.");
        else status.setText(songs.size() + " música(s) encontradas");
        for (int i = 0; i < songs.size(); i++) {
            final int index = i;
            Song s = songs.get(i);
            Button row = button("♫  " + s.title + "\n     " + s.artist);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(5);
            songList.addView(row, lp);
            row.setOnClickListener(v -> playIndex(index));
        }
    }

    private void playIndex(int index) {
        if (index < 0 || index >= songs.size()) return;
        releasePlayer();
        current = index;
        Song song = songs.get(index);
        try {
            player = new MediaPlayer();
            player.setDataSource(this, song.uri);
            player.setOnPreparedListener(mp -> {
                mp.start(); playButton.setText("⏸");
                seekBar.setMax(mp.getDuration()); seekBar.setProgress(0);
                nowTitle.setText(song.title); nowArtist.setText(song.artist);
                status.setText("Tocando • Offline");
                loadCover(song);
                loadLyrics(song);
                try {
                    equalizer = new Equalizer(0, mp.getAudioSessionId());
                    equalizer.setEnabled(true);
                } catch (Exception ignored) { equalizer = null; }
                new Thread(() -> {
                    while (player == mp && mp.isPlaying()) {
                        runOnUiThread(() -> { if (player == mp) seekBar.setProgress(mp.getCurrentPosition()); });
                        try { Thread.sleep(500); } catch (InterruptedException ignored) { break; }
                    }
                }).start();
            });
            player.setOnCompletionListener(mp -> {
                if (current + 1 < songs.size()) playIndex(current + 1);
                else { playButton.setText("▶"); }
            });
            player.prepareAsync();
        } catch (IOException e) {
            status.setText("Não foi possível reproduzir esta faixa.");
            releasePlayer();
        }
    }

    private void loadCover(Song song) {
        Bitmap bitmap = null;
        Uri art = Uri.parse("content://media/external/audio/albumart/" + song.albumId);
        try (java.io.InputStream in = getContentResolver().openInputStream(art)) {
            if (in != null) bitmap = BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {}
        if (bitmap != null) cover.setImageBitmap(bitmap);
        else {
            cover.setImageResource(android.R.drawable.ic_media_play);
            cover.setBackgroundColor(0xFF251A35);
        }
    }

    private void loadLyrics(Song song) {
        String lyrics = prefs.getString("song_" + song.id, "");
        lyricsInput.setText(lyrics);
        lyricsText.setText(lyrics.isEmpty() ? "Nenhuma letra salva para esta música." : lyrics);
    }

    private void togglePlayback() {
        if (player == null) {
            if (!songs.isEmpty()) playIndex(0);
            return;
        }
        if (player.isPlaying()) { player.pause(); playButton.setText("▶"); status.setText("Pausado"); }
        else { player.start(); playButton.setText("⏸"); status.setText("Tocando • Offline"); }
    }

    private void releasePlayer() {
        if (equalizer != null) { try { equalizer.release(); } catch (Exception ignored) {} equalizer = null; }
        if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
