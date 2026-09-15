package com.byteforge.medihive.config;
import com.byteforge.medihive.model.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component
public class Access {
    public String username(){return SecurityContextHolder.getContext().getAuthentication().getName();}
    public String role(){return DemoAccounts.find(username()).role();}
    public String facilityName(){return DemoAccounts.find(username()).facilityName();}
    public boolean warehouse(){return role().equals("WAREHOUSE");}
    public boolean owns(Facility f){return !warehouse() && role().equals(f.getType().name()) && facilityName().equals(f.getName());}
    public boolean canView(Facility f){return warehouse() || owns(f);}
    public void require(Facility f){if(!canView(f)) throw new AccessDeniedException("This facility is outside your account");}
    public void manageInventory(Facility f){if(!warehouse() || f.getType()!=FacilityType.WAREHOUSE || !facilityName().equals(f.getName()))
        throw new AccessDeniedException("Only the Warehouse Admin can edit warehouse inventory");}
    public void supply(Facility f){if(!(warehouse() && f.getType()==FacilityType.WAREHOUSE && facilityName().equals(f.getName())) && !owns(f))
        throw new AccessDeniedException("Only this facility's administrator may approve or dispatch its stock");}
    public void requester(Facility f){if(!owns(f)) throw new AccessDeniedException("Only this hospital or pharmacy's administrator can request for this facility");}
}
