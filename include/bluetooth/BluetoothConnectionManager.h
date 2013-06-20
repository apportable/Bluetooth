#import <BridgeKit/JavaObject.h>
#import "BluetoothSocket.h"

@class BluetoothSocket;

@interface BluetoothConnectionManager : JavaObject
//- (id)initWithDelegate:(id<BluetoothDelegate>)delegate;
- (id)initWithName:(NSString *)name isClient:(BOOL)isClient delegate:(id)delegate;
+ (BOOL)isAvailable;
- (NSString *)getAddress;
- (void)startPublishing;
- (void)stopPublishing;
- (void)startSearching;
- (bool)isConnectedToServer;
- (void)returnRead:(int)length fromSocket:(BluetoothSocket *)socket tag:(long)requestedTag;
- (void)sendDeviceMessage:(BluetoothSocket *)device withMessage:(NSString *)message withTag:(long)tag;
- (void)disconnect;
- (void)shutdown;

@property (nonatomic, retain) NSMutableDictionary *socketMap;

@end





