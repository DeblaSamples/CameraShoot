# CameraShoot

## Preview
![Preview](/img/Screenshot_2017-01-03-10-23-59-85.png “Preview”)

## Output
* Shoot with mask
![Shoot with mask](/img/MIX_2017_01_03_10_24_09.png “Shoot with mask”)

* Shoot without mask
![Shoot without mask](/img/MIX_2017_01_03_10_24_03.png “Shoot without mask”)

## Notice that
This class android.hardware.camera2.CameraCaptureSession used in CameraHelp is defined in android.hardware.camera2.CameraCaptureSession as a hidden class. The defination is
```Java 
/**
 * Temporary for migrating to Callback naming
 * @hide
 */
 public static abstract class StateListener extends StateCallback {}
```
You can replace it with *StateCallback*
