package co.poynt.samples.posconnector;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by dennis on 12/15/15.
 */
public class POSRequest implements Parcelable{

    private String action;
    private Long purchaseAmount;
    private String currency;
    private String referenceId;

    public String getAction() {
        return action;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<POSRequest> CREATOR = new Creator<POSRequest>() {
        @Override
        public POSRequest createFromParcel(Parcel source) {
            POSRequest posRequest = new POSRequest();
            posRequest.action = source.readString();
            posRequest.purchaseAmount = source.readLong();
            posRequest.currency = source.readString();
            posRequest.referenceId = source.readString();

            return posRequest;
        }

        @Override
        public POSRequest[] newArray(int size) {
            return new POSRequest[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(action);
        dest.writeLong(purchaseAmount);
        dest.writeString(currency);
        dest.writeString(referenceId);
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getPurchaseAmount() {
        return purchaseAmount;
    }

    public void setPurchaseAmount(Long purchaseAmount) {
        this.purchaseAmount = purchaseAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

}
