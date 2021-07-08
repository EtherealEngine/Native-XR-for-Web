#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(XRPlugin, "XRPlugin",
           CAP_PLUGIN_METHOD(initialize, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(start, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(stop, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(handleTap, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(accessPermission, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(uploadFiles, CAPPluginReturnPromise);
)
