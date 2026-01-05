#include <stdlib.h>

#include "include/chunk.h"
#include "include/memory.h"
#include "include/vm.h"

/*
 capacity : the number of elements in the array we have allocated
 count : how many of those allocated entries are actually in use
*/

void initChunk(Chunk* chunk) {
    chunk->count = 0;
    chunk->capacity = 0;
    chunk->code = NULL;
    chunk->lines = NULL;
    initValueArray(&chunk->constants);
}

void freeChunk(Chunk* chunk) {
    FREE_ARRAY(uint8_t, chunk->code, chunk->capacity);
    FREE_ARRAY(int, chunk->lines, chunk->capacity);
    freeValueArray(&chunk->constants);
    initChunk(chunk);
}

/*
 When we add an element, if the count is less than the capacity, then there is
 already available space in the array. We store the new element right in there and
 bump the count

 If we don't have enough space in the Chunk :
    1. Allocate a new array with more capacity.
    2. Copy the existing elements from the old array to the new one.
    3. Store the new capacity.
    4. Delete the old array.
    5. Update code to point to the new array.
    6. Store the element in the new array now that there is room.
    7. Update the count.
*/

void writeChunk(Chunk* chunk, uint8_t byte, int line) {
    if (chunk->capacity < chunk->count + 1) {
        int oldCapacity = chunk->capacity;
        chunk->capacity = GROW_CAPACITY(oldCapacity);
        chunk->code = GROW_ARRAY(uint8_t, chunk->code,
            oldCapacity, chunk->capacity);
        chunk->lines = GROW_ARRAY(int, chunk->lines,
            oldCapacity, chunk->capacity);
    }
    chunk->code[chunk->count] = byte;
    chunk->lines[chunk->count] = line;
    chunk->count++;
}

int addConstant(Chunk* chunk, Value value) {
    // push the constant onto the stack temporarily, inorder to avoid GC sweeping this variable
    // i.e., if array size is full then we call realloc to grow tha arrar, which might call GC, which intern can remove this value , even before its getting used
    push(value);                           
    writeValueArray(&chunk->constants, value);
    pop();
    return chunk->constants.count - 1;
}
