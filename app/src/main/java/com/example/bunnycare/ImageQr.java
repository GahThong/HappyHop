package com.example.bunnycare;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.*;

import java.io.FileOutputStream;

public class ImageQr extends PrintDocumentAdapter {

    Bitmap bitmap;

    public ImageQr(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    @Override
    public void onLayout(PrintAttributes o, PrintAttributes n, CancellationSignal c, LayoutResultCallback cb, Bundle b) {

        PrintDocumentInfo info = new PrintDocumentInfo.Builder("file.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build();

        cb.onLayoutFinished(info, true);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor dest, CancellationSignal c, WriteResultCallback cb) {

        try {
            PdfDocument doc = new PdfDocument();

            PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(595, 842, 1).create();

            PdfDocument.Page page = doc.startPage(pi);
            page.getCanvas().drawBitmap(bitmap, 0, 0, null);
            doc.finishPage(page);

            doc.writeTo(new FileOutputStream(dest.getFileDescriptor()));
            doc.close();

            cb.onWriteFinished(pages);

        } catch (Exception e) {
            cb.onWriteFailed(e.toString());
        }
    }
}