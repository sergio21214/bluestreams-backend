package com.spmf.music;

import java.util.ArrayList;
import java.util.List;

public class MusicDownloadBatchResponse {

    public boolean playlist;
    public String message;
    public int total;
    public List<MusicDownloadResponse> jobs = new ArrayList<>();

    public MusicDownloadBatchResponse() {
    }
}