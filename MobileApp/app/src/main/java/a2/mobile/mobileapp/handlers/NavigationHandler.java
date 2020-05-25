package a2.mobile.mobileapp.handlers;

import android.content.Context;
import android.content.Intent;

import com.mapbox.api.directions.v5.models.BannerInstructions;
import com.mapbox.api.directions.v5.models.BannerText;
import com.mapbox.api.directions.v5.models.LegStep;
import com.vuzix.connectivity.sdk.Connectivity;

import java.util.List;

import a2.mobile.mobileapp.constants.NavigationConstants;
import a2.mobile.mobileapp.data.classes.PointOfInterest;
import a2.mobile.mobileapp.enums.PointOfInterestPriorities;

public class NavigationHandler {
    private static Context mContext;

    private static String direction;
    private static String iconName;
    private static String target;
    private static double distance;

    private static String interestPointTitle;
    private static String interestPointDescription;
    private static PointOfInterestPriorities interestPointPriority;

    public static void storeContext(Context context) {
        mContext = context;
    }

    public static void updateDirection(LegStep currentStep) {
        List<BannerInstructions> instructions = currentStep.bannerInstructions();

        if (instructions != null && instructions.size() > 0) {
            BannerInstructions mostImportantInstruction = instructions.get(0);
            BannerText primaryInstruction = mostImportantInstruction.primary();

            direction = primaryInstruction.modifier();
            updateIconName();
        }
    }

    private static void updateIconName() {
        iconName = direction;
        sendDataToBladeApp("icon name", iconName);
    }

    public static void updateDistanceRemaining(int distanceRemaining) {
        distance = distanceRemaining;
        sendDataToBladeApp("distance remaining", String.valueOf(distance));
    }

    public static void updateInterestPoint(PointOfInterest interestPoint) {
        interestPointTitle = interestPoint.title;
        interestPointDescription = interestPoint.interest;
        interestPointPriority = interestPoint.getPriority();

        sendDataToBladeApp("interest point title", interestPointTitle);
        sendDataToBladeApp("interest point description", interestPointDescription);
        sendDataToBladeApp("interest point priority", interestPointPriority.toString());
    }

    private static void sendDataToBladeApp(String name, String data) {
        Intent sendIntent = new Intent(NavigationConstants.SEND_DATA_ACTION);
        sendIntent.setPackage("com.example.bladeapp");
        sendIntent.putExtra(name, data);

        Connectivity.get(mContext).sendBroadcast(sendIntent);
    }
}
