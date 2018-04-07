package com.example.mahanettry.activity;

import com.parrot.arsdk.ardiscovery.UsbAccessoryActivity;

public class UsbAccessoryActivityImpl extends UsbAccessoryActivity
{
    @Override
    protected Class getBaseActivity() {
        return DeviceListActivity.class;
    }
}
