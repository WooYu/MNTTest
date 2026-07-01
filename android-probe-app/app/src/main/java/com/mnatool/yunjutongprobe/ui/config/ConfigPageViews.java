package com.mnatool.yunjutongprobe.ui.config;

import android.graphics.drawable.Drawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/** CONFIG 页表单控件引用，由 {@link ConfigPageController#build()} 填充。 */
public class ConfigPageViews {
    public EditText hostInput;
    public Spinner hostSpinner;
    public EditText portInput;
    public EditText countInput;
    public EditText ppsInput;
    public EditText packetBytesInput;
    public EditText timeoutInput;
    public Spinner weakNetToolSpinner;
    public EditText weakNetLossInput;
    public EditText weakNetDelayInput;
    public EditText weakNetJitterInput;
    public EditText weakNetNoteInput;
    public EditText mqttClientIdInput;
    public EditText mqttPublishTopicInput;
    public EditText mqttSubscribeTopicInput;
    public EditText mqttUsernameInput;
    public EditText mqttPasswordInput;
    public EditText mqttEnvInput;
    public EditText mqttDevicePwdInput;
    public EditText mqttDeviceMacInput;
    public Spinner protocolSpinner;
    public Spinner modeSpinner;
    public Spinner mqttRoleSpinner;
    public LinearLayout mqttConfigContainer;
    public LinearLayout mqttRoleRow;
    public TextView mqttRoleHintView;
    public TextView mqttPairImportHintView;
    public Button startButton;
    public Button modeBaselineButton;
    public Button modeAccelButton;
    public Button presetFieldButton;
    public Button presetLabButton;
    public Switch weakNetSceneSwitch;
    public LinearLayout weakNetCollapsibleBody;
    public LinearLayout mqttAdvancedBody;
    public TextView headerSubtitleView;
    public TextView configProtocolChipView;
    public TextView configModeChipView;
    public TextView configTargetChipView;
    public boolean suppressModeSpinnerCallback;
    public Drawable modeToggleSelectedBg;
    public Drawable modeToggleUnselectedBg;
    public Drawable presetToggleSelectedBg;
    public Drawable presetToggleUnselectedBg;
}
