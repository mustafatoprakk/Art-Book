package com.example.artbook;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class DetailActivity extends AppCompatActivity {

    EditText painterName, year, artName;
    Bitmap selectedImage;
    ImageView imageView;
    Button save;
    SQLiteDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        painterName = findViewById(R.id.painterName);
        artName = findViewById(R.id.artName);
        year = findViewById(R.id.year);
        imageView = findViewById(R.id.selectImage);
        save = findViewById(R.id.save);

        database = this.openOrCreateDatabase("Arts", MODE_PRIVATE, null);

        Intent intent = getIntent();    // verileri gösterme
        String info = intent.getStringExtra("info");

        if (info.matches("new")) {  // yeni bir veri eklendiğinde
            artName.setText("");
            painterName.setText("");
            year.setText("");
            save.setVisibility(View.VISIBLE);

            Bitmap selectimag = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.selectimage);
            imageView.setImageBitmap(selectimag);

        } else {  // eski veriye tıklandığında
            int artId = intent.getIntExtra("artId", 1);
            save.setVisibility(View.INVISIBLE);

            try {

                Cursor cursor = database.rawQuery("SELECT * FROM arts WHERE id = ?", new String[]{String.valueOf(artId)});
                int artNameIx = cursor.getColumnIndex("artname");
                int painterNameIx = cursor.getColumnIndex("paintername");
                int yearIx = cursor.getColumnIndex("year");
                int imageIx = cursor.getColumnIndex("image");

                while (cursor.moveToNext()) {
                    artName.setText(cursor.getString(artNameIx));
                    painterName.setText(cursor.getString(painterNameIx));
                    year.setText(cursor.getString(yearIx));

                    byte[] bytes = cursor.getBlob(imageIx);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    imageView.setImageBitmap(bitmap);
                }

                cursor.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void selectImage(View view) {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) { // izin verilmedi ise

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);// izin ver

        } else { // izin verildi ise
            Intent intentToGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI); // beni al galeriye götür
            startActivityForResult(intentToGallery, 2);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intentToGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI); // beni al galeriye götür
                startActivityForResult(intentToGallery, 2);
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if (requestCode == 2 && resultCode == RESULT_OK && data != null) { // galeriye gidip veri seçilmiş ise
            Uri imageData = data.getData();
            try {

                if (Build.VERSION.SDK_INT >= 28) {   // sdk 28 den büyük ise
                    ImageDecoder.Source source = ImageDecoder.createSource(this.getContentResolver(), imageData);
                    selectedImage = ImageDecoder.decodeBitmap(source);
                    imageView.setImageBitmap(selectedImage);
                } else { // eski versiyon
                    selectedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageData);  // uri bitmap çeviriyorum
                    imageView.setImageBitmap(selectedImage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void save(View view) {   // SQLite

        String aName = artName.getText().toString();
        String pName = painterName.getText().toString();
        String y = year.getText().toString();

        Bitmap smallImage = makeSmallerImage(selectedImage, 300);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();   // göreseli veriye çevirme
        smallImage.compress(Bitmap.CompressFormat.PNG, 50, outputStream);
        byte[] byteArray = outputStream.toByteArray();

        try {   // SQLite save

            database = this.openOrCreateDatabase("Arts", MODE_PRIVATE, null);
            database.execSQL("CREATE TABLE IF NOT EXISTS arts(id INTEGER PRIMARY KEY, artname VARCHAR, paintername Varchar, year VARCHAR, image BLOB)");

            String sqlString = "INSERT INTO arts(artname, paintername, year, image) VALUES (?, ?, ?, ?)";
            SQLiteStatement sqLiteStatement = database.compileStatement(sqlString);   // string teki veriyi sql komutlarıymış gibi çalıştırıyor
            sqLiteStatement.bindString(1, aName);
            sqLiteStatement.bindString(2, pName);
            sqLiteStatement.bindString(3, y);
            sqLiteStatement.bindBlob(4, byteArray);

            sqLiteStatement.execute();

        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent intent=new Intent(DetailActivity.this,MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);  // önceki aktivitileri kapatıyor
        startActivity(intent);
    }

    public Bitmap makeSmallerImage(Bitmap image, int maxSize) {   // Boyutlandırma
        int widh = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) widh / (float) height;  //

        if (bitmapRatio > 1) { // resim yatay
            widh = maxSize;
            height = (int) (widh / bitmapRatio);
        } else { // resim dikey
            height = maxSize;
            widh = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, widh, height, true);
    }

}