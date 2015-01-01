#import "BluetoothReadQueue.h"

@interface QueueObject: NSObject
- (id) init:(NSUInteger)length withTimeout:(NSTimeInterval)timeout tag:(long)tag;
@end

@implementation QueueObject {
@public
    NSUInteger length;
    NSTimeInterval timeout;
    long tag;
}

- (id) init:(NSUInteger)l withTimeout:(NSTimeInterval)time tag:(long)t {
    if ((self = [super init]) ) {
        length = l;
        timeout = time;
        tag = t;
    }

    return self;
}
@end

@interface BluetoothReadQueue ()
@property (nonatomic, retain) NSMutableArray *backingArray;
@end

@implementation BluetoothReadQueue

@synthesize backingArray;

- (id)init
{
    if ((self = [super init]) ) {
        backingArray = [[NSMutableArray alloc] init];
    }

    return self;
}

- (void)enqueue:(NSUInteger)length withTimeout:(NSTimeInterval)timeout tag:(long)tag
{
    QueueObject *qobj = [[QueueObject alloc] init:length withTimeout:timeout tag:tag];
    [backingArray addObject:qobj];
    [qobj release];
}

- (NSUInteger)nextLength
{
    QueueObject *obj = [backingArray objectAtIndex:0];
    return obj->length;
}

- (long)nextTag
{
    QueueObject *obj = [backingArray objectAtIndex:0];
    return obj->tag;
}

- (NSUInteger)dequeue
{
    NSUInteger value = [self nextLength];
    [backingArray removeObjectAtIndex:0];
    return value;
}

- (BOOL)isEmpty
{
    return [backingArray count] == 0;
}

- (void) dealloc
{
    [backingArray release];
    [super dealloc];
}

@end