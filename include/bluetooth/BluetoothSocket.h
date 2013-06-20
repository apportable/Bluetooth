#import <Foundation/NSDictionary.h>
#import <Foundation/NSData.h>
#import "BluetoothReadQueue.h"
#import "BluetoothConnectionManager.h"

@class BluetoothConnectionManager;

@protocol VirtualSocket <NSObject>
- (void)writeData:(NSData *)data withTimeout:(NSTimeInterval)timeout tag:(long)tag;
- (void)readDataToLength:(NSUInteger)length withTimeout:(NSTimeInterval)timeout tag:(long)tag;
- (bool)isConnected;
- (void)disconnect;
@property (nonatomic, retain) id userData;
@end

@interface BluetoothSocket : NSObject<VirtualSocket>
- (id)initWithName:(NSString *)name withConnection:(BluetoothConnectionManager *)connection;
+ (BluetoothSocket *)socketFromName:(NSString *)name;

@property (nonatomic, readonly) NSString *name;
@property (nonatomic, readonly) BluetoothConnectionManager *connection;
@property (nonatomic, retain) NSMutableData *receivedBuffer;
@property (nonatomic, retain) BluetoothReadQueue *readQueue;
@end
