package com.sprd.vsiminterface;

interface IVSIMCallback {
    byte[] uploadAPDU(int slot, in byte[] apdu_req, int apdu_len);
}