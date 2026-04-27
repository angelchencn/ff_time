package oracle.apps.hcm.formulas.core.jersey.util;

import java.util.ArrayList;
import java.util.List;

import oracle.adf.model.binding.PermissionHelper;
import oracle.adf.share.ADFContext;
import oracle.adf.share.security.SecurityContext;
import oracle.adf.share.security.authorization.ADFPermission;
import oracle.adf.share.security.authorization.RestServicePermission;

import org.apache.commons.lang3.StringUtils;

public class FastFormulaResourceUtil {
    public static final String FAST_FORMULA_RES_NAME = "fastFormulaAssistants";
    public static final String FASTFORMULA_ENTRIES = "/hcmRestApi/redwood/11.13.18.05/fastFormulaAssistants";
    
    public final static List<String> validResources = new ArrayList<String>();

    static {
        validResources.add(FASTFORMULA_ENTRIES);
    }
        
    public static boolean isValidResource(String pResourcePath) {
        boolean isValid = Boolean.FALSE;
        if (StringUtils.isBlank(pResourcePath))
            return isValid;

        for (String resource : validResources) {
            if (pResourcePath.startsWith(resource)) {
                isValid = Boolean.TRUE;
                break;
            }
        }
        return isValid;
    }
    
    public static boolean hasPermission(String pResourcePath) {
            boolean isAllowed= false;
           SecurityContext secCtx = ADFContext.getCurrent().getSecurityContext();
           ADFPermission rsPermission=rsPermission = new RestServicePermission(FAST_FORMULA_RES_NAME,RestServicePermission.GET_ACTION);
           if(pResourcePath.contains(FAST_FORMULA_RES_NAME)){
                rsPermission = new RestServicePermission(FAST_FORMULA_RES_NAME,RestServicePermission.GET_ACTION);
               if (PermissionHelper.hasPermission(secCtx, rsPermission)) {
                   isAllowed= true;
               }
           }
          return isAllowed;
    }

}
